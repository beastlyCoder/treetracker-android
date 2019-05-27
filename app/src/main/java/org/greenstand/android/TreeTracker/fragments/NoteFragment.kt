package org.greenstand.android.TreeTracker.fragments

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_note.*
import kotlinx.android.synthetic.main.fragment_note.view.*
import org.greenstand.android.TreeTracker.R
import org.greenstand.android.TreeTracker.activities.CameraActivity
import org.greenstand.android.TreeTracker.activities.MainActivity
import org.greenstand.android.TreeTracker.application.Permissions
import org.greenstand.android.TreeTracker.application.TreeTrackerApplication
import org.greenstand.android.TreeTracker.database.AppDatabase
import org.greenstand.android.TreeTracker.database.entity.LocationEntity
import org.greenstand.android.TreeTracker.database.entity.NoteEntity
import org.greenstand.android.TreeTracker.database.entity.TreeNoteEntity
import org.greenstand.android.TreeTracker.utilities.Utils
import org.greenstand.android.TreeTracker.utilities.Utils.Companion.dateFormat
import org.greenstand.android.TreeTracker.utilities.ValueHelper
import org.koin.android.ext.android.getKoin
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class NoteFragment : Fragment(), OnClickListener, OnCheckedChangeListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private var mImageView: ImageView? = null
    private var mCurrentPhotoPath: String? = null
    private val mImageBitmap: Bitmap? = null
    private val fragment: Fragment? = null
    private val bundle: Bundle? = null
    private val fragmentTransaction: androidx.fragment.app.FragmentTransaction? = null
    private var userId: Long = 0
    private var mSharedPreferences: SharedPreferences? = null
    private var treeIdStr: String? = null
    private var mTreeIsMissing: Boolean = false

    /* Photo album for this application */
    private val albumName: String
        get() = getString(R.string.album_name)

    private val albumDir: File?
        get() {
            var storageDir: File? = null

            if (Environment.MEDIA_MOUNTED == Environment
                    .getExternalStorageState()
            ) {

                val cw = ContextWrapper(activity!!.applicationContext)
                storageDir = cw.getDir("treeImages", Context.MODE_PRIVATE)

                if (storageDir != null) {
                    if (!storageDir.mkdirs()) {
                        if (!storageDir.exists()) {
                            Timber.d("CameraSample failed to create directory")
                            return null
                        }
                    }
                }

            } else {
                Log.v(
                    getString(R.string.app_name),
                    "External storage is not mounted READ/WRITE."
                )
            }

            return storageDir
        }

    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDatabase = getKoin().get()
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.clear()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val v = inflater.inflate(R.layout.fragment_note, container, false)

        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        activity?.toolbarTitle?.setText(R.string.tree_preview)
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val extras = arguments

        treeIdStr = extras!!.getString(ValueHelper.TREE_ID)

        mSharedPreferences = (activity as AppCompatActivity).getSharedPreferences(
            "org.greenstand.android", Context.MODE_PRIVATE
        )

        userId = mSharedPreferences!!.getLong(ValueHelper.MAIN_USER_ID, -1)

        fragmentNoteSave.setOnClickListener(this@NoteFragment)

        fragmentNoteTreeMissing.setOnClickListener(this@NoteFragment)

        fragmentNoteMissingTreeCheckbox.setOnCheckedChangeListener(this@NoteFragment)

        mImageView = v.fragmentNoteImage


        val updatedTrees = appDatabase.treeDao().getUpdatedTrees(treeIdStr)

        updatedTrees.forEach {
            mCurrentPhotoPath = it.name

            val noImage = v.fragmentNoteNoImage

            if (mCurrentPhotoPath != null) {
                setPic()
                noImage.visibility = View.INVISIBLE
            } else {
                noImage.visibility = View.VISIBLE
            }

            val lat = it.latitude
            val lon = it.longitude

            MainActivity.mCurrentTreeLocation = Location("") // Empty location
            MainActivity.mCurrentTreeLocation!!.latitude = lat
            MainActivity.mCurrentTreeLocation!!.longitude = lon
        }

        return v
    }

    override fun onClick(v: View) {

        v.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

        when (v.id) {

            R.id.fragmentNoteSave ->

                if (mTreeIsMissing) {
                    val builder = AlertDialog.Builder(activity)

                    builder.setTitle(R.string.tree_missing)
                    builder.setMessage(R.string.you_are_about_to_mark_this_tree_as_missing)

                    builder.setPositiveButton(R.string.yes) { dialog, which ->
                        saveToDb()

                        Toast.makeText(activity, "Tree saved", Toast.LENGTH_SHORT)
                            .show()
                        val manager = activity!!.supportFragmentManager
                        val second = manager.getBackStackEntryAt(1)
                        manager.popBackStack(second.id, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

                        dialog.dismiss()
                    }


                    builder.setNegativeButton(R.string.no) { dialog, which ->
                        // Code that is executed when clicking NO

                        dialog.dismiss()
                    }

                    val alert = builder.create()
                    alert.show()
                } else {
                    saveToDb()

                    Toast.makeText(activity, "Tree saved", Toast.LENGTH_SHORT)
                        .show()

                    val manager = activity!!.supportFragmentManager
                    val second = manager.getBackStackEntryAt(1)
                    manager.popBackStack(second.id, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

                }
            R.id.fragmentNoteTreeMissing -> {
            }
        }//			takePicture();

    }


    private fun takePicture() {
        if (ActivityCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity!!, arrayOf(Manifest.permission.CAMERA),
                Permissions.MY_PERMISSION_CAMERA
            )
        } else {
            val takePictureIntent = Intent(activity, CameraActivity::class.java)
            startActivityForResult(takePictureIntent, ValueHelper.INTENT_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Permissions.MY_PERMISSION_CAMERA && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePicture()
        }
    }

    @Throws(IOException::class)
    private fun setUpPhotoFile(): File {

        val f = createImageFile()
        mCurrentPhotoPath = f.absolutePath

        return f
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss")
            .format(Date())
        val imageFileName = ValueHelper.JPEG_FILE_PREFIX + timeStamp + "_"
        val albumF = albumDir
        return File.createTempFile(
            imageFileName,
            ValueHelper.JPEG_FILE_SUFFIX, albumF
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {

            mCurrentPhotoPath = data!!.getStringExtra(ValueHelper.TAKEN_IMAGE_PATH)

            if (mCurrentPhotoPath != null) {
                activity?.fragmentNote?.visibility = View.VISIBLE
                setPic()
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            //			if (((RelativeLayout)getActivity().findViewById(R.id.fragment_new_tree)).getVisibility() != View.VISIBLE) {
            //				getActivity().getSupportFragmentManager().popBackStack();
            //			}
        }

    }

    private fun saveToDb() {
        var date = Date()
        val calendar = Calendar.getInstance()
        calendar.time = date

        val timeToNextUpdate = mSharedPreferences!!.getInt(
            ValueHelper.TIME_TO_NEXT_UPDATE_ADMIN_DB_SETTING, mSharedPreferences!!.getInt(
                ValueHelper.TIME_TO_NEXT_UPDATE_GLOBAL_SETTING,
                ValueHelper.TIME_TO_NEXT_UPDATE_DEFAULT_SETTING
            )
        )

        calendar.add(Calendar.DAY_OF_MONTH, timeToNextUpdate)
        date = calendar.time as Date

        // location
        val location = LocationEntity(
            MainActivity.mCurrentLocation!!.accuracy.toInt(),
            MainActivity.mCurrentLocation!!.latitude,
            MainActivity.mCurrentLocation!!.longitude,
            userId
        )

        val locationId = appDatabase.locationDao().insert(location)

        Timber.d("locationId " + java.lang.Long.toString(locationId))

        // note
        val noteEntity = NoteEntity(0, activity?.fragmentNoteNote?.text.toString(), Utils.dateFormat.format(Date()), userId)

        val noteId = appDatabase.noteDao().insert(noteEntity)
        Timber.d("noteId " + java.lang.Long.toString(noteId))


        // tree
        val treeEntity = appDatabase.treeDao().getTreeByID(treeIdStr!!.toLong()).first()
        treeEntity.locationId = locationId.toInt()
        treeEntity.isSynced = false
        treeEntity.isPriority = false

        if (mTreeIsMissing) {
            treeEntity.isMissing = true
            treeEntity.causeOfDeath = noteId.toInt()
        }
        treeEntity.timeForUpdate = dateFormat.format(date)

        treeEntity.timeUpdated = dateFormat.format(Date())

        appDatabase.treeDao().updateTree(treeEntity)

        // tree_note
        val treeNoteEntity = TreeNoteEntity(noteId, treeIdStr!!.toLong())
        appDatabase.noteDao().insert(treeNoteEntity)
    }

    private fun setPic() {

        /* There isn't enough memory to open up more than a couple camera photos */
        /* So pre-scale the target bitmap into which the file is decoded */

        /* Get the size of the ImageView */

        /* Get the size of the image */
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)
        val imageWidth = bmOptions.outWidth

        // Calculate your sampleSize based on the requiredWidth and
        // originalWidth
        // For e.g you want the width to stay consistent at 500dp
        val requiredWidth = (500 * resources.displayMetrics.density).toInt()

        var sampleSize = Math.ceil((imageWidth.toFloat() / requiredWidth.toFloat()).toDouble()).toInt()

        // If the original image is smaller than required, don't sample
        if (sampleSize < 1) {
            sampleSize = 1
        }

        bmOptions.inSampleSize = sampleSize
        bmOptions.inPurgeable = true
        bmOptions.inPreferredConfig = Bitmap.Config.RGB_565
        bmOptions.inJustDecodeBounds = false

        /* Decode the JPEG file into a Bitmap */
        val bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)

        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(mCurrentPhotoPath)
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        val orientString = exif!!.getAttribute(ExifInterface.TAG_ORIENTATION)
        val orientation = if (orientString != null)
            Integer.parseInt(orientString)
        else
            ExifInterface.ORIENTATION_NORMAL
        var rotationAngle = 0
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
            rotationAngle = 90
        if (orientation == ExifInterface.ORIENTATION_ROTATE_180)
            rotationAngle = 180
        if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
            rotationAngle = 270

        Timber.tag("rotationAngle").d(rotationAngle.toString())

        val matrix = Matrix()
        matrix.setRotate(
            rotationAngle.toFloat(), bitmap.width.toFloat() / 2,
            bitmap.height.toFloat() / 2
        )
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0,
            bmOptions.outWidth, bmOptions.outHeight, matrix, true
        )

        /* Associate the Bitmap to the ImageView */
        mImageView!!.setImageBitmap(rotatedBitmap)
        mImageView!!.visibility = View.VISIBLE
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            R.id.fragmentNoteMissingTreeCheckbox -> {
                mTreeIsMissing = isChecked
                val noteTxt = activity?.fragmentNoteNote

                if (isChecked) {
                    noteTxt?.hint = activity!!.resources.getString(R.string.cause_of_death)
                } else {
                    noteTxt?.hint = activity!!.resources.getString(R.string.add_text_note)
                }
            }

            else -> {
            }
        }

    }

    companion object {

        fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int, reqHeight: Int
        ): Int {
            // Raw height and width of image
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {

                // Calculate ratios of height and width to requested height and
                // width
                val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
                val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())

                // Choose the smallest ratio as inSampleSize value, this will
                // guarantee
                // a final image with both dimensions larger than or equal to the
                // requested height and width.
                inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
            }

            return inSampleSize
        }
    }

}// some overrides and settings go here
