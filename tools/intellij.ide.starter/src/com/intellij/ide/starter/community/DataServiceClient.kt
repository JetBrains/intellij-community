package com.intellij.ide.starter.community

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.community.model.ReleaseInfo
import com.intellij.ide.starter.utils.HttpClient
import org.apache.http.client.methods.HttpGet
import java.nio.file.Path


class DataServiceClient {
  companion object {

    private const val DATA_SERVICE_URL = "https://data.services.jetbrains.com"

    fun getReleases(request: PublicIdeResolver.ProductInfoRequestParameters): Map<String, List<ReleaseInfo>> {
      return HttpClient.sendRequest(HttpGet("$DATA_SERVICE_URL/products/releases$request").apply {
        addHeader("Content-Type", "application/json")
        addHeader("Accept", "application/json")
      }) {
        jacksonObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .registerModule(JavaTimeModule())
          .readValue(it.entity.content, object : TypeReference<Map<String, List<ReleaseInfo>>>() {})
      }
    }

    fun downloadIDE(url: String, filePath: Path) {
      HttpClient.download(url, filePath)
    }
  }
}
