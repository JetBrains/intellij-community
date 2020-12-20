package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName

data class StandardV2Licenses(

    @SerializedName("main_license")
    val mainLicense: StandardV2LinkedFile?,

    @SerializedName("other_licenses")
    val otherLicenses: List<StandardV2LinkedFile>?
)
