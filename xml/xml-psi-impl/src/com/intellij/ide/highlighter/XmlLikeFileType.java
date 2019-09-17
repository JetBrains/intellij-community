// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.lang.Language;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.XmlCharsetDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class XmlLikeFileType extends LanguageFileType {
  protected XmlLikeFileType(@NotNull Language language) {
    super(language);
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull final byte[] content) {
    LoadTextUtil.DetectResult guessed = LoadTextUtil.guessFromContent(file, content);
    String charset =
      guessed.hardCodedCharset != null
      ? guessed.hardCodedCharset.name()
      : XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    return charset == null ? CharsetToolkit.UTF8 : charset;
  }

  @Override
  public Charset extractCharsetFromFileContent(final Project project, @Nullable final VirtualFile file, @NotNull final CharSequence content) {
    String name = XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    Charset charset = CharsetToolkit.forName(name);
    return charset == null ? StandardCharsets.UTF_8 : charset;
  }
}
