package org.greenstand.android.TreeTracker.api.models.responses;

import com.google.gson.annotations.SerializedName;

/**
 * Created by lei on 11/10/17.
 */

public class PostResult {

    @SerializedName("status")
    private int status;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
