// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status.bean

import junit.framework.TestCase
import org.assertj.core.api.Assertions

class JetStatSettingsTest : TestCase() {
  fun `test correct settings json parsing`() {
    val status = "ok"
    val urlForCompressed = "http://test.jetstat-resty.aws.intellij.net/uploadstats/compressed"
    val json =
      """{
          "status": "$status", 
          "salt": "sdfs", 
          "experimentVersion": 2,
          "performExperiment": false,
          "url": "http://test.jetstat-resty.aws.intellij.net/uploadstats",
          "urlForZipBase64Content": "$urlForCompressed"
      }"""

    val result = JetStatSettingsDeserializer.deserialize(json)
    Assertions.assertThat(result).isNotNull
    Assertions.assertThat(result!!.status).isEqualTo(status)
    Assertions.assertThat(result.urlForZipBase64Content).isEqualTo(urlForCompressed)
  }

  fun `test partial settings json parsing`() {
    val json =
      """{
          "salt": "sdfs", 
          "experimentVersion": 2,
          "performExperiment": false,
          "url": "http://test.jetstat-resty.aws.intellij.net/uploadstats"
      }"""

    val result = JetStatSettingsDeserializer.deserialize(json)
    Assertions.assertThat(result).isNotNull
    Assertions.assertThat(result!!.status).isNotEqualTo("ok")
    Assertions.assertThat(result.urlForZipBase64Content).isEqualTo("")
  }
}