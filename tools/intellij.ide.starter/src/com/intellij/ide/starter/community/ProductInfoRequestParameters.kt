package com.intellij.ide.starter.community

import org.apache.http.client.utils.URIBuilder

data class ProductInfoRequestParameters(val code: String,
                                        val type: String = "release",
                                        val majorVersion: String = "",
                                        val build: String = "") {
  fun toUriQuery(): URIBuilder {
    val builder = URIBuilder()

    if (code.isNotBlank()) builder.addParameter("code", code)
    if (type.isNotBlank()) builder.addParameter("type", type)
    if (majorVersion.isNotBlank()) builder.addParameter("majorVersion", majorVersion)
    if (build.isNotBlank()) builder.addParameter("build", build)

    return builder
  }

  override fun toString(): String {
    return toUriQuery().toString()
  }
}
