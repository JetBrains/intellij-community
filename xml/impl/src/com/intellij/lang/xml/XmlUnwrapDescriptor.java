/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.codeInsight.unwrap.LanguageUnwrappers;
import com.intellij.codeInsight.unwrap.UnwrapDescriptor;
import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XmlUnwrapDescriptor implements UnwrapDescriptor {
  @NotNull
  @Override
  public List<Pair<PsiElement, Unwrapper>> collectUnwrappers(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset();

    PsiElement e1 = file.findElementAt(offset);
    if (e1 != null) {
      Language language = e1.getParent().getLanguage();
      if (language != file.getLanguage()) {
        UnwrapDescriptor unwrapDescriptor = LanguageUnwrappers.INSTANCE.forLanguage(language);
        if (unwrapDescriptor != null && !(unwrapDescriptor instanceof XmlUnwrapDescriptor)) {
          return unwrapDescriptor.collectUnwrappers(project, editor, file);
        }
      }
    }

    List<Pair<PsiElement, Unwrapper>> result = new ArrayList<>();

    FileViewProvider viewProvider = file.getViewProvider();

    for (Language language : viewProvider.getLanguages()) {
      UnwrapDescriptor unwrapDescriptor = LanguageUnwrappers.INSTANCE.forLanguage(language);
      if (unwrapDescriptor instanceof XmlUnwrapDescriptor) {
        PsiElement e = viewProvider.findElementAt(offset, language);

        PsiElement tag = PsiTreeUtil.getParentOfType(e, XmlTag.class);
        while (tag != null) {
          if (XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode()) != null) { // Exclude implicit tags suck as 'jsp:root'
            result.add(new Pair<>(tag, new XmlEnclosingTagUnwrapper()));
          }
          tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class);
        }
      }
    }

    Collections.sort(result, (o1, o2) -> o2.first.getTextOffset() - o1.first.getTextOffset());

    return result;
  }

  @Override
  public boolean showOptionsDialog() {
    return true;
  }

  @Override
  public boolean shouldTryToRestoreCaretPosition() {
    return false;
  }
}
