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
package com.jetbrains.python.refactoring.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapDescriptorBase;
import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nullable;

/**
 * user : ktisha
 */
public class PyUnwrapDescriptor extends UnwrapDescriptorBase{
  @Override
  protected Unwrapper[] createUnwrappers() {
    return new Unwrapper[] {
      new PyIfUnwrapper(),
      new PyWhileUnwrapper(),
      new PyElseRemover(),
      new PyElseUnwrapper(),
      new PyElIfUnwrapper(),
      new PyElIfRemover(),
      new PyTryUnwrapper(),
      new PyForUnwrapper(),
      new PyWithUnwrapper()
    };
  }

  @Override
  @Nullable
  protected PsiElement findTargetElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement endElement = PyUtil.findElementAtOffset(file, offset);
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection() && selectionModel.getSelectionStart() < offset) {
      PsiElement startElement = PyUtil.findElementAtOffset(file, selectionModel.getSelectionStart());
      if (startElement != null && startElement != endElement && startElement.getTextRange().getEndOffset() == offset) {
        return startElement;
      }
    }
    return endElement;
  }
}
