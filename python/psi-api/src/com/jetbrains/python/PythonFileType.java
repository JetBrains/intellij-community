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
package com.jetbrains.python;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharSequenceReader;
import icons.PythonPsiApiIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonFileType extends LanguageFileType {
  private static final Pattern ENCODING_PATTERN = Pattern.compile("coding[:=]\\s*([-\\w.]+)");
  public static final int MAX_CHARSET_ENCODING_LINE = 2;

  public static PythonFileType INSTANCE = new PythonFileType();

  public PythonFileType() {
    this(PythonLanguage.INSTANCE);
  }

  public PythonFileType(Language language) {
    super(language);
  }

  @NotNull
  public String getName() {
    return "Python";
  }

  @NotNull
  public String getDescription() {
    return "Python files";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "py";
  }

  @NotNull
  public Icon getIcon() {
    return PythonPsiApiIcons.PythonFile;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    if (CharsetToolkit.hasUTF8Bom(content)) {
      return CharsetToolkit.UTF8;
    }
    ByteBuffer bytes = ByteBuffer.wrap(content, 0, Math.min(256, content.length));
    String decoded = CharsetToolkit.UTF8_CHARSET.decode(bytes).toString();
    return getCharsetFromEncodingDeclaration(StringUtil.convertLineSeparators(decoded));
  }

  @Override
  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull CharSequence content) {
    final String charsetName = getCharsetFromEncodingDeclaration(content);
    if (charsetName == null) {
      return null;
    }
    try {
      return Charset.forName(charsetName);
    }
    catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      return null;
    }
  }

  @Nullable
  public static String getCharsetFromEncodingDeclaration(@NotNull PsiFile file) {
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    final String content;
    if (document != null && document.getLineCount() > MAX_CHARSET_ENCODING_LINE) {
      final int offset = document.getLineEndOffset(MAX_CHARSET_ENCODING_LINE);
      content = document.getText(TextRange.create(0, offset));
    }
    else {
      content = file.getText();
    }
    return getCharsetFromEncodingDeclaration(content);
  }

  @Nullable
  private static String getCharsetFromEncodingDeclaration(@Nullable CharSequence content) {
    if (content == null || content.length() == 0) {
      return null;
    }
    try {
      final BufferedReader reader = new BufferedReader(new CharSequenceReader(content));
      try {
        for (int i = 0; i < MAX_CHARSET_ENCODING_LINE; i++) {
          final String line = reader.readLine();
          if (line == null) {
            return null;
          }
          final Matcher matcher = ENCODING_PATTERN.matcher(line);
          if (matcher.find()) {
            final String charset = matcher.group(1);
            return normalizeCharset(charset);
          }
        }
      } finally {
        reader.close();
      }
    }
    catch (IOException ignored) {
    }
    return null;
  }

  @Nullable
  private static String normalizeCharset(String charset) {
    if (charset == null) {
      return null;
    }
    charset = charset.toLowerCase();
    if ("latin-1".equals(charset)) {
      return "iso-8859-1";
    }
    return charset;
  }
}
