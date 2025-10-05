// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.XmlCharsetDetector;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.intellij.util.CharsetDetector.detectCharsetFromMetaTag;

public class HtmlFileType extends XmlLikeFileType {
  public static final @NonNls String DOT_DEFAULT_EXTENSION = ".html";

  public static final HtmlFileType INSTANCE = new HtmlFileType();

  private HtmlFileType() {
    super(HTMLLanguage.INSTANCE);
  }

  protected HtmlFileType(Language language) {
    super(language);
  }

  @Override
  public @NotNull String getName() {
    return "HTML";
  }

  @Override
  public @NotNull String getDescription() {
    return XmlCoreBundle.message("filetype.html.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "html";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Html;
  }

  @Override
  public String getCharset(final @NotNull VirtualFile file, final byte @NotNull [] content) {
    LoadTextUtil.DetectResult guessed = LoadTextUtil.guessFromContent(file, content);
    String charset =
      guessed.hardCodedCharset != null
      ? guessed.hardCodedCharset.name()
      : XmlCharsetDetector.extractXmlEncodingFromProlog(content);

    if (charset != null) return charset;
    @NonNls String strContent = new String(content, StandardCharsets.ISO_8859_1);
    Charset c = detectCharsetFromMetaTag(strContent);
    return c == null ? null : c.name();
  }

  @Override
  public Charset extractCharsetFromFileContent(final @Nullable Project project,
                                               final @Nullable VirtualFile file,
                                               final @NotNull CharSequence content) {
    String name = XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    Charset charset = CharsetToolkit.forName(name);

    if (charset != null) {
      return charset;
    }
    return detectCharsetFromMetaTag(content);
  }
}
