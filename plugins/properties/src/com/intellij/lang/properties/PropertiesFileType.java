/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.nio.charset.Charset;

/**
 * @author max
 */
public class PropertiesFileType extends LanguageFileType {
  public static final Icon FILE_ICON = IconLoader.getIcon("/fileTypes/properties.png");
  public static final LanguageFileType FILE_TYPE = new PropertiesFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "properties";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = "."+DEFAULT_EXTENSION;

  private PropertiesFileType() {
    super(PropertiesLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return "Properties";
  }

  @NotNull
  public String getDescription() {
    return PropertiesBundle.message("properties.files.file.type.description");
  }

  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  public Icon getIcon() {
    return FILE_ICON;
  }

  public String getCharset(@NotNull VirtualFile file, final byte[] content) {
    Charset charset = EncodingManager.getInstance().getDefaultCharsetForPropertiesFiles(file);
    String defaultCharsetName = charset == null ? CharsetToolkit.getDefaultSystemCharset().name() : charset.name();
    return defaultCharsetName;
  }
}
