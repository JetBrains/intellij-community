/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.PythonFileType;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class PyFileImpl extends PsiFileBase implements PyFile {
  public PyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, PythonLanguage.getInstance());
  }

  @NotNull
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  public String toString() {
    return "PyFile:" + getName();
  }

  public Icon getIcon(int flags) {
    return PythonFileType.INSTANCE.getIcon();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof PyElementVisitor) {
      ((PyElementVisitor)visitor).visitPyFile(this);
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PsiElement[] children = getChildren();
    for (PsiElement child : children) {
      if (!child.processDeclarations(processor, substitutor, lastParent, place)) {
        return false;
      }
    }

    final String fileName = getName();
    if (!fileName.equals("__builtin__.py")) {
      final Project project = getProject();
      final PsiFile[] builtinFiles = FilenameIndex.getFilesByName(project, "__builtin__.py", GlobalSearchScope.allScope(project));
      if (builtinFiles.length > 0 && builtinFiles [0] instanceof PyFile) {
        if (!builtinFiles [0].processDeclarations(processor, substitutor, null, place)) return false;
      }
    }

    return true;
  }

  @Nullable
  public <T extends PyElement> T getContainingElement(Class<T> aClass) {
    return null;
  }

  @Nullable
  public PyElement getContainingElement(TokenSet tokenSet) {
    return null;
  }

  @PsiCached
  public List<PyStatement> getStatements() {
    List<PyStatement> stmts = new ArrayList<PyStatement>();
    for (PsiElement child : getChildren()) {
      if (child instanceof PyStatement) {
        PyStatement statement = (PyStatement)child;
        stmts.add(statement);
      }
    }
    return stmts;
  }

  public PythonLanguage getPyLanguage() {
    return (PythonLanguage)getLanguage();
  }
}
