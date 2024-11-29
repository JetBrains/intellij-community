// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.Language;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PropertiesFileType extends LanguageFileType {
  public static final LanguageFileType INSTANCE = new PropertiesFileType();
  public static final String DEFAULT_EXTENSION = "properties";
  public static final String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;

  private static final Logger log = Logger.getInstance(PropertiesFileType.class);

  private final Set<VirtualFile> myChangedFiles = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private PropertiesFileType() {
    super(PropertiesLanguage.INSTANCE);

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        if (event.getFile().getFileType() == INSTANCE) {
          myChangedFiles.add(event.getFile());
        }
      }
    });
  }

  protected PropertiesFileType(@NotNull Language language) {
    super(language);
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
      charset = file.isCharsetSet() ? file.getCharset() : null;
      if (charset == null || myChangedFiles.remove(file)) {
        charset = getDefaultCharset();
        if (StandardCharsets.UTF_8.equals(charset)) {
          try(InputStream stream = file.getInputStream()) {
            content = stream.readAllBytes();
            if (content.length > 0 &&
                CharsetUtil.findUnmappableCharacters(ByteBuffer.wrap(content), StandardCharsets.UTF_8) != null) {
              charset = StandardCharsets.ISO_8859_1;
            }
          }
          catch (IOException e) {
            log.error("Failed to read content from file: " + file.getPath(), e);
          }
        }
      }
    }

    if (EncodingRegistry.getInstance().isNative2Ascii(file)) {
      if(!(charset instanceof Native2AsciiCharset)) {
        charset = Native2AsciiCharset.wrap(charset);
      }
    } else {
      charset = Native2AsciiCharset.nativeToBaseCharset(charset);
    }
    return charset.name();
  }

  public @NotNull Charset getDefaultCharset() {
    if (Registry.is("properties.file.encoding.legacy.support", true)) {
      return StandardCharsets.ISO_8859_1;
    }
    else {
      return StandardCharsets.UTF_8;
    }
  }
}
