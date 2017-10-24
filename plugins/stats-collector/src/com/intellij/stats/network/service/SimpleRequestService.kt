/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.stats.network.service

import com.google.common.net.HttpHeaders
import com.intellij.openapi.diagnostic.logger
import org.apache.commons.codec.binary.Base64OutputStream
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream

class SimpleRequestService: RequestService() {
    private val LOG = logger<SimpleRequestService>()

    override fun post(url: String, params: Map<String, String>): ResponseData? {
        val form = Form.form()
        params.forEach { form.add(it.key, it.value) }
        return try {
            val response = Request.Post(url).bodyForm(form.build()).execute()
            val httpResponse = response.returnResponse()
            ResponseData(httpResponse.status())
        } catch (e: IOException) {
            LOG.debug(e)
            null
        }
    }

    override fun postZipped(url: String, file: File): ResponseData? {
        return try {
            val zippedArray = getZippedContent(file)
            val request = Request.Post(url).bodyByteArray(zippedArray).apply {
                addHeader(BasicHeader(HttpHeaders.CONTENT_ENCODING, "gzip"))
            }

            val response = request.execute().returnResponse()
            ResponseData(response.status(), response.text())
        } catch (e: IOException) {
            LOG.debug(e)
            null
        }
    }

    private fun getZippedContent(file: File): ByteArray {
        val fileText = file.readText()
        return GzipBase64Compressor.compress(fileText)
    }

    override fun post(url: String, file: File): ResponseData? {
        return try {
            val response = Request.Post(url).bodyFile(file, ContentType.TEXT_HTML).execute()
            val httpResponse = response.returnResponse()
            val text = EntityUtils.toString(httpResponse.entity)
            ResponseData(httpResponse.statusLine.statusCode, text)
        }
        catch (e: IOException) {
            LOG.debug(e)
            null
        }
    }

    override fun get(url: String): ResponseData? {
        return try {
            var data: ResponseData? = null
            Request.Get(url).execute().handleResponse {
                val text = EntityUtils.toString(it.entity)
                data = ResponseData(it.statusLine.statusCode, text)
            }
            data
        } catch (e: IOException) {
            LOG.debug(e)
            null
        }
    }
}


data class ResponseData(val code: Int, val text: String = "") {
    fun isOK() = code in 200..299
}


object GzipBase64Compressor {
    fun compress(text: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val base64Stream = GZIPOutputStream(Base64OutputStream(outputStream))
        base64Stream.write(text.toByteArray())
        base64Stream.close()
        return outputStream.toByteArray()
    }
}

fun HttpResponse.text(): String = EntityUtils.toString(entity)
fun HttpResponse.status(): Int = statusLine.statusCode