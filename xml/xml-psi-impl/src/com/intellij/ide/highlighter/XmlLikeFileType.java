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

public abstract class XmlLikeFileType extends LanguageFileType {
  public XmlLikeFileType(Language language) {
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
    return charset == null ? CharsetToolkit.UTF8_CHARSET : charset;
  }

  public boolean isCaseSensitive() {
    return false;
  }
}
