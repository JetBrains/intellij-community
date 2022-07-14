package com.intellij.ide.starter.community

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.community.model.ReleaseInfo
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import org.apache.http.client.methods.HttpGet


class JetBrainsDataServiceClient {
  companion object {

    private const val DATA_SERVICE_URL = "https://data.services.jetbrains.com"

    fun getReleases(request: ProductInfoRequestParameters): Map<String, List<ReleaseInfo>> {
      val getUrlToJbDataServices = "$DATA_SERVICE_URL/products/releases$request"
      logOutput("Requesting products by url: $getUrlToJbDataServices")

      return HttpClient.sendRequest(
        HttpGet(getUrlToJbDataServices).apply {
          addHeader("Content-Type", "application/json")
          addHeader("Accept", "application/json")
        }) {
        jacksonObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .registerModule(JavaTimeModule())
          .readValue(it.entity.content, object : TypeReference<Map<String, List<ReleaseInfo>>>() {})
      }
    }
  }
}
