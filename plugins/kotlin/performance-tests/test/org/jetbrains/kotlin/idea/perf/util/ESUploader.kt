// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.util

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object ESUploader {
    var host: String? = null
    var username: String? = null
    var password: String? = null

    var indexName = "kotlin_ide_benchmarks"

    private val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()

    init {
        host = System.getenv("ES_HOSTNAME") ?: System.getenv("es.hostname")
        username = System.getenv("ES_USERNAME") ?: System.getenv("es.username")
        password = System.getenv("ES_PASSWORD") ?: System.getenv("es.password")
        logMessage { "initialized es details $username @ $host" }
    }

    fun upload(benchmark: Benchmark) {
        if (host == null) {
            logMessage { "ES host is not specified, ${benchmark.id()} would not be uploaded" }
            return
        }

        val url = "$host/$indexName/_doc/${benchmark.id()}"
        val auth = if (username != null && password != null) {
            Credentials.basic(username!!, password!!);
        } else {
            null
        }
        val json = kotlinJsonMapper.writeValueAsString(benchmark)

        val body: RequestBody = json.toRequestBody(JSON)
        val request: Request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .also { builder ->
                auth?.let {
                    builder.header("Authorization", it)
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val code = response.code
            val string = response.body?.string()
            logMessage { "$code -> $string" }
            if (code != 200 && code != 201) {
                throw IllegalStateException("Error code $code -> $string")
            }
        }
    }
}