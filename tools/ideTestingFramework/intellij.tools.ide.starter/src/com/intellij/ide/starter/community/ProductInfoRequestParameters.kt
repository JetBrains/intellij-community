package com.intellij.ide.starter.community

import org.apache.http.client.utils.URIBuilder

data class ProductInfoRequestParameters(
  val type: String,
  val snapshot: String = "release",
  // e.g "2022.2"
  val majorVersion: String = "",
  // e.g  "221.5591.52",
  val buildNumber: String = "",
  // e.g "2022.1.1"
  val versionNumber: String = ""
) {
  private fun toUriQuery(): URIBuilder {
    val builder = URIBuilder()

    // API seems to filter only by code and type. It doesn't respond to majorVersion, build or version params

    if (type.isNotBlank()) builder.addParameter("code", type)
    if (snapshot.isNotBlank()) builder.addParameter("type", snapshot)

    return builder
  }

  override fun toString(): String {
    return toUriQuery().toString()
  }
}
