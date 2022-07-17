// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class PropertiesFileType extends LanguageFileType {
  public static final LanguageFileType INSTANCE = new PropertiesFileType();
  public static final String DEFAULT_EXTENSION = "properties";
  public static final String DOT_DEFAULT_EXTENSION = "."+DEFAULT_EXTENSION;
  public static final Charset PROPERTIES_DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;

  private PropertiesFileType() {
    super(PropertiesLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "Properties";
  }

  @Override
  public @NotNull String getDescription() {
    return PropertiesBundle.message("filetype.properties.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Properties;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    Charset charset = EncodingRegistry.getInstance().getDefaultCharsetForPropertiesFiles(file);
    if (charset == null) {
      charset = PROPERTIES_DEFAULT_CHARSET;
    }
    if (EncodingRegistry.getInstance().isNative2Ascii(file)) {
      charset = Native2AsciiCharset.wrap(charset);
    }
    return charset.name();
  }
}
