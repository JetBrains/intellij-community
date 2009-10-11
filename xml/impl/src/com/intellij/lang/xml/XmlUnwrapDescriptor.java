/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.unwrap.UnwrapDescriptor;
import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;

import java.util.Collections;
import java.util.List;

public class XmlUnwrapDescriptor implements UnwrapDescriptor {
  public List<Pair<PsiElement, Unwrapper>> collectUnwrappers(Project project, Editor editor, PsiFile file) {
    PsiElement e = findElement(editor, file);
    if (e == null) return Collections.emptyList();

    return Collections.singletonList(new Pair<PsiElement, Unwrapper>(e, new XmlEnclosingTagUnwrapper()));
  }

  private PsiElement findElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement e = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(e, XmlTag.class);
  }

  public boolean showOptionsDialog() {
    return false;
  }

  public boolean shouldTryToRestoreCaretPosition() {
    return false;
  }
}
