// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class PropertiesFileType extends LanguageFileType {
  public static final LanguageFileType INSTANCE = new PropertiesFileType();
  public static final String DEFAULT_EXTENSION = "properties";
  public static final String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;

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
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.PropertiesFileType);
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    Charset charset = EncodingRegistry.getInstance().getDefaultCharsetForPropertiesFiles(file);
    if (charset == null) {
      charset = getDefaultCharset();
      if (content.length > 0 &&
          StandardCharsets.UTF_8.equals(charset) &&
          CharsetUtil.findUnmappableCharacters(ByteBuffer.wrap(content), StandardCharsets.UTF_8) != null) {
        charset = StandardCharsets.ISO_8859_1;
      }
    }
    if (EncodingRegistry.getInstance().isNative2Ascii(file)) {
      charset = Native2AsciiCharset.wrap(charset);
    }
    return charset.name();
  }

  public @NotNull Charset getDefaultCharset() {
    if (Registry.is("properties.file.encoding.legacy.support", false)) {
      return StandardCharsets.ISO_8859_1;
    }
    else {
      return StandardCharsets.UTF_8;
    }
  }
}
