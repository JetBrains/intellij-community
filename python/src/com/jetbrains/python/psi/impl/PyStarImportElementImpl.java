/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.toolbox.ChainIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author dcheryasov
 */
public class PyStarImportElementImpl extends PyElementImpl implements PyStarImportElement {

  public PyStarImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    if (getParent() instanceof PyFromImportStatement) {
      PyFromImportStatement fromImportStatement = (PyFromImportStatement)getParent();
      final List<PsiElement> importedFiles = fromImportStatement.resolveImportSourceCandidates();
      ChainIterable<PyElement> chain = new ChainIterable<PyElement>();
      for (PsiElement importedFile : new HashSet<PsiElement>(importedFiles)) { // resolver gives lots of duplicates
        final PsiElement source = PyUtil.turnDirIntoInit(importedFile);
        if (source instanceof PyFile) {
          chain.add(((PyFile) source).iterateNames());
        }
      }
      return chain;
    }
    return Collections.emptyList();
  }

  @Nullable
  public PsiElement getElementNamed(final String name) {
    if (PyUtil.isClassPrivateName(name)) {
      return null;
    }
    if (getParent() instanceof PyFromImportStatement) {
      PyFromImportStatement fromImportStatement = (PyFromImportStatement)getParent();
      final List<PsiElement> importedFiles = fromImportStatement.resolveImportSourceCandidates();
      for (PsiElement importedFile : new HashSet<PsiElement>(importedFiles)) { // resolver gives lots of duplicates
        final PsiElement source = PyUtil.turnDirIntoInit(importedFile);
        if (source instanceof PyFile) {
          PyFile sourceFile = (PyFile)source;
          final PyModuleType moduleType = new PyModuleType(sourceFile);
          final List<? extends RatedResolveResult> results = moduleType.resolveMember(name, null, AccessDirection.READ,
                                                                                      PyResolveContext.defaultContext());
          final PsiElement result = results != null && !results.isEmpty() ? results.get(0).getElement() : null;
          if (result != null) {
            final List<String> all = sourceFile.getDunderAll();
            if (all != null && !all.contains(name)) {
              continue;
            }
            return result;
          }
        }
      }
    }
    return null;
  }

  public boolean mustResolveOutside() {
    return true; // we don't have children, but... 
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      private String getName() {
        PyFromImportStatement elt = PsiTreeUtil.getParentOfType(PyStarImportElementImpl.this, PyFromImportStatement.class);
        if (elt != null) { // always? who knows :)
          PyReferenceExpression imp_src = elt.getImportSource();
          if (imp_src != null) {
            return PyResolveUtil.toPath(imp_src);
          }
        }
        return "<?>";
      }

      public String getPresentableText() {
        return getName();
      }

      public String getLocationString() {
        return "| " + "from " + getName() + " import *";
      }

      public Icon getIcon(final boolean open) {
        return null;
      }
    };
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStarImportElement(this);
  }
}
