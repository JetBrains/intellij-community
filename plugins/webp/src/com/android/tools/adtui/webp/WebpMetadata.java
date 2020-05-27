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
package com.android.tools.adtui.webp;

import com.intellij.ide.ApplicationInitializedListener;
import org.w3c.dom.Node;

import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;

public class WebpMetadata extends IIOMetadata implements ApplicationInitializedListener {
  public static final String WEBP_FORMAT_LOWER_CASE = "webp";
  public static final String WEBP_FORMAT_UPPER_CASE = "WEBP";
  public static final String[] WEBP_FORMAT_NAMES = new String[] {WEBP_FORMAT_UPPER_CASE, WEBP_FORMAT_LOWER_CASE};
  public static final String EXT_WEBP = WEBP_FORMAT_LOWER_CASE;
  public static final String[] WEBP_SUFFIXES = new String[] { EXT_WEBP};
  public static final String[] WEBP_MIME_TYPES = new String[] {"image/webp"};
  public static final String WEBP_VENDOR = "Google LLC";
  public static final float DEFAULT_ENCODING_QUALITY = 0.75f;
  public static final boolean DEFAULT_LOSSLESS = true;

  @Override
  public void componentsInitialized() {
    ensureWebpRegistered();
  }

  /**
   * Ensures that service providers are registered.
   */
  public static void ensureWebpRegistered() {
    IIORegistry.getDefaultInstance().registerServiceProvider(new WebpImageReaderSpi(), ImageReaderSpi.class);
    IIORegistry.getDefaultInstance().registerServiceProvider(new WebpImageWriterSpi(), ImageWriterSpi.class);
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public Node getAsTree(String formatName) {
    return new IIOMetadataNode(nativeMetadataFormatName);
  }

  @Override
  public void mergeTree(String formatName, Node root) throws IIOInvalidTreeException {
  }

  @Override
  public void reset() {
  }
}
