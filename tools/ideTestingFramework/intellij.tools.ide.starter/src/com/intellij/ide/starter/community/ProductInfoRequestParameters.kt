package com.intellij.ide.starter.community

import org.apache.http.client.utils.URIBuilder

data class ProductInfoRequestParameters(
  val code: String,
  val type: String = "release",
  // e.g "2022.2"
  val majorVersion: String = "",
  // e.g  "221.5591.52",
  val build: String = "",
  // e.g "2022.1.1"
  val version: String = ""
) {
  fun toUriQuery(): URIBuilder {
    val builder = URIBuilder()

    // API seems to filter only by code and type. It doesn't respond to majorVersion, build or version params

    if (code.isNotBlank()) builder.addParameter("code", code)
    if (type.isNotBlank()) builder.addParameter("type", type)

    return builder
  }

  override fun toString(): String {
    return toUriQuery().toString()
  }
}
