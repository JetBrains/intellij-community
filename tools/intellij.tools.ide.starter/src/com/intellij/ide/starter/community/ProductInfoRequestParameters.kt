package com.intellij.ide.starter.community

import org.apache.http.client.utils.URIBuilder

data class ProductInfoRequestParameters(
  /** Eg: IDEA - "IU", PyCharm - "PY" */
  val type: String,
  /** Eg: "release", "eap", "preview" */
  val snapshot: String = "release",
  /** e.g "2022.2" */
  val majorVersion: String = "",
  /** e.g  "221.5591.52" */
  val buildNumber: String = "",
  /** e.g "2022.1.1" */
  val versionNumber: String = "",
) {
  /**
   * API seems to filter only by code and type. It doesn't respond to majorVersion, build or version params
   */
  fun toUriQueries(): List<String> {
    if (snapshot.isBlank()) {
      // when there is no snapshot type defined, the API only returns a list of all releases from snapshot type "release"
      // so it is necessary to accumulate data from both types
      return listOf("release", "eap", "preview").map { buildUriQuery(it) }
    }
    return listOf(buildUriQuery(snapshot))
  }

  private fun buildUriQuery(snapshotType: String): String {
    val builder = URIBuilder()
    if (type.isNotBlank()) builder.addParameter("code", type)
    builder.addParameter("type", snapshotType)
    return builder.toString()
  }
}
