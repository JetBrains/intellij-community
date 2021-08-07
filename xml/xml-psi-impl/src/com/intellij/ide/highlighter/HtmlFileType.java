/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.XmlCharsetDetector;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HtmlFileType extends XmlLikeFileType {
  @NonNls public static final String DOT_DEFAULT_EXTENSION = ".html";

  public static final HtmlFileType INSTANCE = new HtmlFileType();

  private HtmlFileType() {
    super(HTMLLanguage.INSTANCE);
  }

  protected HtmlFileType(Language language) {
    super(language);
  }

  @Override
  @NotNull
  public String getName() {
    return "HTML";
  }

  @Override
  @NotNull
  public String getDescription() {
    return XmlPsiBundle.message("filetype.html.description");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "html";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Html;
  }

  @Override
  public String getCharset(@NotNull final VirtualFile file, final byte @NotNull [] content) {
    LoadTextUtil.DetectResult guessed = LoadTextUtil.guessFromContent(file, content);
    String charset =
      guessed.hardCodedCharset != null
      ? guessed.hardCodedCharset.name()
      : XmlCharsetDetector.extractXmlEncodingFromProlog(content);

    if (charset != null) return charset;
    @NonNls String strContent = new String(content, StandardCharsets.ISO_8859_1);
    Charset c = HtmlUtil.detectCharsetFromMetaTag(strContent);
    return c == null ? null : c.name();
  }

  @Override
  public Charset extractCharsetFromFileContent(@Nullable final Project project, @Nullable final VirtualFile file, @NotNull final CharSequence content) {
    String name = XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    Charset charset = CharsetToolkit.forName(name);

    if (charset != null) {
      return charset;
    }
    return HtmlUtil.detectCharsetFromMetaTag(content);
  }
}
