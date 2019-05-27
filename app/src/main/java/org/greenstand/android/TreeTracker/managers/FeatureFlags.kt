package org.greenstand.android.TreeTracker.managers

import org.greenstand.android.TreeTracker.BuildConfig

object FeatureFlags {
    val DEBUG_ENABLED: Boolean = BuildConfig.BUILD_TYPE == "dev"
    val TREE_HEIGHT_FEATURE_ENABLED: Boolean = BuildConfig.TREE_HEIGHT_FEATURE_ENABLED
    val TREE_NOTE_FEATURE_ENABLED: Boolean = BuildConfig.TREE_NOTE_FEATURE_ENABLED
    val FABRIC_ENABLED: Boolean = BuildConfig.ENABLE_FABRIC
    val HIGH_GPS_ACCURACY: Boolean = BuildConfig.GPS_ACCURACY
}