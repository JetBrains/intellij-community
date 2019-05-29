/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.intellij.plugins.markdown.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.MarkdownIcons;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MarkdownFileType extends LanguageFileType {
  public static final MarkdownFileType INSTANCE = new MarkdownFileType();

  private MarkdownFileType() {
    super(MarkdownLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "Markdown";
  }

  @NotNull
  @Override
  public String getDescription() {
    return MarkdownBundle.message("markdown.file.type.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "md";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return MarkdownIcons.MarkdownPlugin;
  }
}
