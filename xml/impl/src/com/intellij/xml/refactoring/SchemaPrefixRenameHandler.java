/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.xml.refactoring;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class SchemaPrefixRenameHandler extends VariableInplaceRenameHandler {
  
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    SchemaPrefixReference ref = getReference(dataContext);
    return ref != null && ref.resolve() != null;
  }

  @Nullable
  private static SchemaPrefixReference getReference(DataContext dataContext) {
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    return getReference(file, editor);
  }

  @Nullable
  private static SchemaPrefixReference getReference(PsiFile file, Editor editor) {
    if (file != null && editor != null) {
      int offset = editor.getCaretModel().getOffset();
      PsiReference reference = file.findReferenceAt(offset);
      if (reference instanceof PsiMultiReference) {
        PsiReference[] references = ((PsiMultiReference)reference).getReferences();
        for (PsiReference psiReference : references) {
          if (psiReference instanceof SchemaPrefixReference) {
            return (SchemaPrefixReference)psiReference;
          }
        }
      }
      if (reference instanceof SchemaPrefixReference) {
        return (SchemaPrefixReference)reference;
      }
    }
    return null;

  }

  @Override
  protected VariableInplaceRenamer createRenamer(PsiElement elementToRename, Editor editor) {
    SchemaPrefixReference reference = getReference(elementToRename.getContainingFile(), editor);
    if (reference != null) {
      SchemaPrefix prefix = reference.resolve();
      if (prefix != null) {
        return new VariableInplaceRenamer(prefix, editor) {
          @Override
          protected void addReferenceAtCaret(Collection<PsiReference> refs) {}

        };
      }
    }
    return null;
  }
}
