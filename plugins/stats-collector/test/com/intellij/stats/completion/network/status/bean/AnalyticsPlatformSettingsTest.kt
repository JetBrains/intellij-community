// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status.bean

import com.intellij.lang.Language
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat

class AnalyticsPlatformSettingsTest : TestCase() {

  private fun generateJson(releaseType: String, majorVersionBorders: String): String =
    """{
          "productCode": "IU",
          "versions": [{
            "releaseType": $releaseType,
            "majorBuildVersionBorders": $majorVersionBorders,
            "completionLanguage": "ANY",
            "fromBucket": 0,
            "toBucket": 255,
            "endpoint": "https://prod.fus.aws.intellij.net/oso/v1/send/"
          }]
    }"""

  fun `test correct settings json parsing`() {
    val json = generateJson(
      releaseType = "ALL",
      majorVersionBorders = "{ \"majorVersionFrom\": \"2020.1\" }"
    )
    val result = AnalyticsPlatformSettingsDeserializer.deserialize(json)
    assertThat(result).isNotNull
    assertThat(result!!.versions[0].releaseType).isEqualTo(ReleaseType.ALL)
  }

  fun `test correct settings json parsing 2`() {
    val json = generateJson(
      releaseType = "RELEASE",
      majorVersionBorders = "{ \"majorVersionFrom\": \"2020.1\", \"majorVersionTo\": \"2020.2\" }"
    )
    val result = AnalyticsPlatformSettingsDeserializer.deserialize(json)
    assertThat(result).isNotNull
    assertThat(result!!.versions[0].releaseType).isEqualTo(ReleaseType.RELEASE)
  }

  fun `test empty settings json parsing`() {
    val json =
      """
      """
    val result = AnalyticsPlatformSettingsDeserializer.deserialize(json)
    assertThat(result).isNull()
  }

  fun `test settings json with default values parsing`() {
    val endpoint = "https://prod.fus.aws.intellij.net/oso/v1/send/"
    val json =
      """{
          "versions": [{
            "endpoint": "$endpoint"
          }]
      }"""
    val result = AnalyticsPlatformSettingsDeserializer.deserialize(json)
    assertThat(result).isNotNull
    assertThat(result!!.versions[0].releaseType).isEqualTo(ReleaseType.ALL)
    assertThat(result.versions[0].completionLanguage).isEqualTo(Language.ANY)
    assertThat(result.versions[0].fromBucket).isEqualTo(0)
    assertThat(result.versions[0].toBucket).isEqualTo(255)
    assertThat(result.versions[0].majorBuildVersionBorders.majorVersionFrom).isNull()
    assertThat(result.versions[0].majorBuildVersionBorders.majorVersionTo).isNull()
    assertThat(result.versions[0].endpoint).isEqualTo(endpoint)
  }
}