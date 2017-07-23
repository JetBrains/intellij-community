/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.ipnb.psi;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;

import java.util.List;

public class IpnbSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
    final SearchScope scope = queryParameters.getEffectiveSearchScope();
    if (scope instanceof LocalSearchScope) return;
    final PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof IpnbPyTargetExpression) && !(element instanceof IpnbPyFunction)) {
      return;
    }
    final PsiFile file = element.getContainingFile();
    if (file instanceof IpnbPyFragment) {
      final IpnbFilePanel panel = ((IpnbPyFragment)file).getFilePanel();
      final List<IpnbEditablePanel> panels = panel.getIpnbPanels();

      for (IpnbEditablePanel editablePanel : panels) {
        if (!(editablePanel instanceof IpnbCodePanel)) continue;
        final Editor editor = ((IpnbCodePanel)editablePanel).getEditor();
        final IpnbPyFragment psiFile = (IpnbPyFragment)PsiDocumentManager.getInstance(element.getProject()).getPsiFile(editor.getDocument());
        if (psiFile == null) continue;
        psiFile.accept(new PyRecursiveElementVisitor() {
          @Override
          public void visitPyElement(PyElement node) {
            super.visitElement(node);
            final PsiReference reference = node.getReference();
            if (reference == null) return;
            if (element.equals(reference.resolve())) {
              consumer.process(reference);
            }
          }
        });
      }
    }
  }
}
