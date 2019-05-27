package org.greenstand.android.TreeTracker.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amazonaws.AmazonClientException
import kotlinx.coroutines.*
import org.greenstand.android.TreeTracker.R
import org.greenstand.android.TreeTracker.api.RetrofitApi
import org.greenstand.android.TreeTracker.api.DOSpaces
import org.greenstand.android.TreeTracker.api.models.requests.AttributeRequest
import org.greenstand.android.TreeTracker.api.models.requests.NewTreeRequest
import org.greenstand.android.TreeTracker.api.models.requests.RegistrationRequest
import org.greenstand.android.TreeTracker.application.TreeTrackerApplication
import org.greenstand.android.TreeTracker.database.AppDatabase
import org.greenstand.android.TreeTracker.database.dao.TreeDto
import org.greenstand.android.TreeTracker.database.entity.PlanterDetailsEntity
import org.greenstand.android.TreeTracker.database.entity.PlanterIdentificationsEntity
import org.greenstand.android.TreeTracker.managers.TreeManager
import org.greenstand.android.TreeTracker.managers.UserManager
import org.greenstand.android.TreeTracker.utilities.Utils
import timber.log.Timber
import java.io.File
import java.io.IOException

class DataViewModel(private val userManager: UserManager,
                    private val api: RetrofitApi,
                    private val treeManager: TreeManager,
                    private val appDatabase: AppDatabase) : CoroutineViewModel() {

    private val treeInfoLiveData = MutableLiveData<TreeData>()
    private val toastLiveData = MutableLiveData<Int>()
    private val isSyncingLiveData = MutableLiveData<Boolean>()

    val treeData: LiveData<TreeData> = treeInfoLiveData
    val toasts: LiveData<Int> = toastLiveData
    val isSyncing: LiveData<Boolean> = isSyncingLiveData

    private var currentJob: Job? = null

    init {
        updateData()
    }

    fun sync() {
        launch {
            val treesToSync = withContext(Dispatchers.IO) { appDatabase.treeDao().getToSyncTreeCount() }
            when (treesToSync) {
                0 -> {
                    currentJob?.cancel()
                    toastLiveData.value = R.string.nothing_to_sync
                }
                else -> {
                    toastLiveData.value = R.string.sync_started
                    startDataSynchronization()
                }
            }
        }
    }

    private fun updateData() {
        launch(Dispatchers.IO) {

            val treeCount = appDatabase.treeDao().getTotalTreeCount()
            val syncedTreeCount = appDatabase.treeDao().getSyncedTreeCount()
            val notSyncedTreeCount = appDatabase.treeDao().getToSyncTreeCount()

            withContext(Dispatchers.Main) {
                treeInfoLiveData.value = TreeData(totalTrees = treeCount,
                                                  treesToSync = notSyncedTreeCount,
                                                  treesSynced = syncedTreeCount)
            }
        }
    }

    private fun startDataSynchronization() {
        isSyncingLiveData.value = true
        currentJob = launch {

            val isAuthenticated = withContext(Dispatchers.IO) { api.authenticateDevice() }

            if (!isAuthenticated) {
                toastLiveData.value = R.string.sync_failed
                isSyncingLiveData.value = false
                return@launch
            }

            withContext(Dispatchers.IO) {

                uploadUserIdentifications()

                uploadPlanterIdentifications()

                uploadNewTrees()
            }

            toastLiveData.value = R.string.sync_successful
            isSyncingLiveData.value = false
        }
    }

    private suspend fun uploadUserIdentifications() {
        // Upload all user registration data that hasn't been uploaded yet
        val registrations = appDatabase.planterDao().getPlanterRegistrationsToUpload()
        registrations.forEach {
            runCatching {
                async(Dispatchers.IO) { uploadPlanterRegistration(it) }.await()
            }
        }
    }

    private suspend fun uploadPlanterIdentifications() {
        // Get all planter_identifications without a photo_url
        val planterCursor: List<PlanterIdentificationsEntity> = appDatabase.planterDao().getPlanterIdentificationsToUpload()

        planterCursor.forEach { planterIndentification ->
            try {
                val imageUrl = async(Dispatchers.IO) { uploadPlanterPhoto(planterIndentification) }.await()
                planterIndentification.photoUrl = imageUrl
                appDatabase.planterDao().updatePlanterIdentification(planterIndentification)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private suspend fun uploadNewTrees() {
        val treeList = appDatabase.treeDao().getTreesToUpload()

        Timber.tag("DataFragment").d("treeCursor: $treeList")
        Timber.tag("DataFragment").d("treeCursor: " + treeList.size)

        treeList.onEach {
            val treeRequest = createTreeRequest(it)

            val uploadSuccess = if (treeRequest != null) {
                async(Dispatchers.IO) { uploadNextTreeAsync(it, treeRequest) }.await()
            } else {
                Timber.e("TreeRequest creation failed")
                false
            }

            if (uploadSuccess) {
                updateData()
            } else {
                Timber.e("NewTree upload failed")
            }
        }
    }

    private suspend fun uploadPlanterRegistration(planterDetailsEntity: PlanterDetailsEntity): Boolean {
        val registration = RegistrationRequest(
            planterIdentifier = planterDetailsEntity.identifier,
            firstName = planterDetailsEntity.firstName,
            lastName = planterDetailsEntity.lastName,
            organization = planterDetailsEntity.organization,
            location = planterDetailsEntity.location
        )

        return try {
            api.createPlanterRegistration(registration)
            planterDetailsEntity.uploaded = true
            appDatabase.planterDao().updatePlanterDetails(planterDetailsEntity)
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun uploadPlanterPhoto(planterIdentificationsEntity: PlanterIdentificationsEntity): String? {
        /**
         * Implementation for saving image into DigitalOcean Spaces.
         */
        val imagePath = planterIdentificationsEntity.photoPath
        val imageUrl: String
        if (imagePath != null && imagePath != "") { // don't crash if image path is empty
            try {
                imageUrl = async(Dispatchers.IO) { DOSpaces.instance().put(imagePath) }.await()
            } catch (ace: AmazonClientException) {
                Timber.d(
                    "Caught an AmazonClientException, which " +
                            "means the client encountered " +
                            "an internal error while trying to " +
                            "communicate with S3, " +
                            "such as not being able to access the network."
                )
                Timber.d("Error Message: " + ace.message)
                return null
            }

            Timber.d("imageUrl: $imageUrl")
            return imageUrl
        } else {
            return null
        }

    }

    private suspend fun uploadNextTreeAsync(uploadedtree: TreeDto, newTreeRequest: NewTreeRequest): Boolean {
        Timber.tag("DataFragment").d("tree_id: $uploadedtree.tree_id")
        /*
        * Save to the API
        */
        val treeIdResponse: Int? = try {
            api.createTree(newTreeRequest)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }

        if (treeIdResponse != null) {
            val missingTrees = appDatabase.treeDao().getMissingTreeByID(uploadedtree.tree_id)
            if (missingTrees.isNotEmpty()) {
                appDatabase.treeDao().deleteTree(missingTrees.first())
                val photos = appDatabase.photoDao().getPhotosByTreeId(uploadedtree.tree_id)

                photos.forEach {
                    try {
                        val file = File(it.name)
                        file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
            } else {
                val treeList = appDatabase.treeDao().getTreeByID(uploadedtree.tree_id)
                val treeEntity = treeList.first()
                treeEntity.isSynced = true
                treeEntity.mainDbId = treeIdResponse
                appDatabase.treeDao().updateTree(treeEntity)


                val outdatedPhotos =
                    appDatabase.photoDao().getOutdatedPhotos(uploadedtree.tree_id)

                outdatedPhotos.forEach {

                    try {
                        val file = File(it.name)
                        val deleted = file.delete()
                        if (deleted) {
                            Timber.tag("DataFragment").d("delete file: ${it.name}")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
            }
            return true
        } else {
            return false
        }
    }

    private suspend fun createTreeRequest(treeDto: TreeDto): NewTreeRequest? {

        suspend fun getImageUrl(): String? {
            /**
             * Implementation for saving image into DigitalOcean Spaces.
             */
            val imagePath = treeDto.name

            if (imagePath.isNullOrEmpty()) return null

            // don't crash if image path is empty
            return try {
                withContext(Dispatchers.IO) {
                    DOSpaces.instance().put(imagePath).also { Timber.d("imageUrl: $it") }
                }
            } catch (ace: AmazonClientException) {
                Timber.e(
                    "Caught an AmazonClientException, which " +
                            "means the client encountered " +
                            "an internal error while trying to " +
                            "communicate with S3, " +
                            "such as not being able to access the network."
                )
                Timber.e("Error Message: ${ace.message}")
                null
            }
        }

        val imageUrl = getImageUrl() ?: return null

        val attributesList = treeManager.getTreeAttributes(treeDto.tree_id)
        var attributesRequest =  mutableListOf<AttributeRequest>()
        for(attribute in attributesList){
            attributesRequest.add( AttributeRequest(key=attribute.key, value=attribute.value))
        }

        return NewTreeRequest(
            uuid = treeDto.uuid,
            imageUrl = imageUrl,
            userId = userManager.userId.toInt(),
            sequenceId = treeDto.tree_id,
            lat = treeDto.latitude,
            lon = treeDto.longitude,
            gpsAccuracy = treeDto.accuracy,
            planterIdentifier = treeDto.planter_identifier,
            planterPhotoUrl = treeDto.planter_photo_url,
            timestamp = Utils.convertDateToTimestamp(treeDto.tree_time_created!!),
            note = treeDto.note,
            attributes = attributesRequest
        )
    }

    override fun onCleared() {
        stopSyncing()
    }

    fun stopSyncing() {
        currentJob?.cancel()
    }

}

data class TreeData(val treesSynced: Int,
                    val treesToSync: Int,
                    val totalTrees: Int)