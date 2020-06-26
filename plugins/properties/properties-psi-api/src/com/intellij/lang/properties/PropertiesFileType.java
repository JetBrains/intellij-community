// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.charset.Charset;

public final class PropertiesFileType extends LanguageFileType {
  public static final LanguageFileType INSTANCE = new PropertiesFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "properties";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = "."+DEFAULT_EXTENSION;

  private PropertiesFileType() {
    super(PropertiesLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "Properties";
  }

  @Override
  @NotNull
  public String getDescription() {
    return PropertiesBundle.message("properties.files.file.type.description");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Properties;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    LoadTextUtil.DetectResult guessed = LoadTextUtil.guessFromContent(file, content);
    Charset charset = guessed.hardCodedCharset == null ? EncodingRegistry.getInstance().getDefaultCharsetForPropertiesFiles(file) : guessed.hardCodedCharset;
    if (charset == null) {
      charset = CharsetToolkit.getDefaultSystemCharset();
    }
    if (EncodingRegistry.getInstance().isNative2Ascii(file)) {
      charset = Native2AsciiCharset.wrap(charset);
    }
    return charset.name();
  }
}
