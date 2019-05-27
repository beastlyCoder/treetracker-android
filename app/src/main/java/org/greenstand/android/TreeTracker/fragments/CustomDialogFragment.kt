package org.greenstand.android.TreeTracker.fragments

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import org.greenstand.android.TreeTracker.R

class CustomDialogFragment : DialogFragment()
{
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?    {
        val view = inflater!!.inflate(R.layout.layout_dialog, container, false)

        val btnAccept = view.findViewById<View>(R.id.buttonAccept) as Button

        val textViewContent = view.findViewById<View>(R.id.textViewContent) as TextView
        textViewContent.text = "Phone number is used for users with no email in areas not as fortunate as those in the USA."

        btnAccept.setOnClickListener{
            dismiss();
        }

        return view;
    }
}