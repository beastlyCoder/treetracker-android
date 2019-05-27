package org.greenstand.android.TreeTracker.api.models.requests

import android.os.Build
import com.google.gson.annotations.SerializedName
import org.greenstand.android.TreeTracker.BuildConfig

data class DeviceRequest(@SerializedName("app_version")
                         val app_version: String = BuildConfig.VERSION_NAME,
                         @SerializedName("app_build")
                         val app_build: Int = BuildConfig.VERSION_CODE,
                         @SerializedName("manufacturer")
                         val manufacturer: String = Build.MANUFACTURER,
                         @SerializedName("brand")
                         val brand: String = Build.BRAND,
                         @SerializedName("model")
                         val model: String = Build.MODEL,
                         @SerializedName("hardware")
                         val hardware: String = Build.HARDWARE,
                         @SerializedName("device")
                         val device: String = Build.DEVICE,
                         @SerializedName("serial")
                         val serial: String = Build.SERIAL,
                         @SerializedName("androidRelease")
                         val androidRelease: String = Build.VERSION.RELEASE,
                         @SerializedName("androidSdkVersion")
                         val androidSdkVersion: Int = Build.VERSION.SDK_INT)