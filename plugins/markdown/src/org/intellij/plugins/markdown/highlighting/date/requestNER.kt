// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.date

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class NERAnnotation(val start: Int, val end: Int,
                                  val label: String, val text: String)

internal fun requestNER(texts: Collection<String>): Collection<Collection<NERAnnotation>>? {
  val requestBody = Json.stringify(NERRequest(texts)).toRequestBody(jsonType)

  val postRequest = Request.Builder()
    .url("https://staging.ner.grazie.iml.aws.intellij.net/annotate")
    .post(requestBody)
    .build()

  val json = client.newCall(postRequest).execute().use { it.body?.string() }

  return json?.let { Json.parse<NERResponse>(it) }?.annotations
}


private val client = OkHttpClient()
private val jsonType = "application/json; charset=utf-8".toMediaTypeOrNull()

private data class NERRequest(val texts: Collection<String>) {
  @Suppress("SpellCheckingInspection", "unused")
  private val key = "EIUMD2HU7749LE0BFK42557L5J5NRMG5"
}

private data class NERResponse(val annotations: Collection<Collection<NERAnnotation>>)

private object Json {
  private val gson = Gson()

  fun <T> stringify(value: T): String {
    return gson.toJson(value)
  }

  inline fun <reified T> parse(json: String): T? {
    return gson.fromJson(json, T::class.java)
  }
}