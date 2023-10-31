/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.webp

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import org.w3c.dom.Node
import java.nio.file.Path
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.spi.ImageWriterSpi

class WebpMetadata : IIOMetadata() {
  companion object {
    const val WEBP_FORMAT_LOWER_CASE: String = "webp"
    private const val WEBP_FORMAT_UPPER_CASE: String = "WEBP"
    val WEBP_FORMAT_NAMES: Array<String> = arrayOf(WEBP_FORMAT_UPPER_CASE, WEBP_FORMAT_LOWER_CASE)
    private const val EXT_WEBP: String = WEBP_FORMAT_LOWER_CASE

    val WEBP_SUFFIXES: Array<String> = arrayOf(EXT_WEBP)
    val WEBP_MIME_TYPES: Array<String> = arrayOf("image/webp")

    const val WEBP_VENDOR: String = "Google LLC"
    const val DEFAULT_ENCODING_QUALITY: Float = 0.75f
    const val DEFAULT_LOSSLESS: Boolean = true

    /**
     * Ensures that service providers are registered.
     */
    @JvmStatic
    fun ensureWebpRegistered() {
      val defaultInstance = IIORegistry.getDefaultInstance()
      defaultInstance.registerServiceProvider(WebpImageReaderSpi(), ImageReaderSpi::class.java)
      defaultInstance.registerServiceProvider(WebpImageWriterSpi(), ImageWriterSpi::class.java)
    }
  }

  override fun isReadOnly(): Boolean = false

  override fun getAsTree(formatName: String): Node = IIOMetadataNode(nativeMetadataFormatName)

  override fun mergeTree(formatName: String, root: Node) {
  }

  override fun reset() {
  }
}

private class WebpMetadataRegistrar : ApplicationLoadListener {
  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
    WebpMetadata.ensureWebpRegistered()
  }
}

