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
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NotNull;

public class MarkdownFile extends PsiFileBase implements MarkdownPsiElement {
  public MarkdownFile(FileViewProvider viewProvider) {
    super(viewProvider, MarkdownLanguage.INSTANCE);
    // It is a little bit hacky way to set a flavour but there was no any way to do it before
    putUserData(MarkdownParserManager.FLAVOUR_DESCRIPTION, MarkdownParserManager.FLAVOUR);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitMarkdownFile(this);
      return;
    }

    visitor.visitFile(this);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return MarkdownFileType.INSTANCE;
  }
}
