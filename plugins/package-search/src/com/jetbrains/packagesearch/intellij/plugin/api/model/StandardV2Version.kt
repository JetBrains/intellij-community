package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls

data class StandardV2Version(

  @NlsSafe
  @SerializedName("version")
  val version: String,

  @SerializedName("last_changed")
  val lastChanged: Long?,

  @SerializedName("stable")
  val stable: Boolean?,

  @NonNls
  @SerializedName("repository_ids")
  val repositoryIds: List<String>?,

  @SerializedName("artifacts")
  val artifacts: List<StandardV2Artifact>?
)

data class StandardV2Artifact(

  @NonNls
  @SerializedName("sha1")
  val sha1: String?,

  @NonNls
  @SerializedName("sha256")
  val sha256: String?,

  @NonNls
  @SerializedName("md5")
  val md5: String?,

  @NlsSafe
  @SerializedName("packaging")
  val packaging: String?,

  @NlsSafe
  @SerializedName("classifier")
  val classifier: String?
)
