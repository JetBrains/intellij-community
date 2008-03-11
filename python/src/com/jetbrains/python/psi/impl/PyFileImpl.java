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
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      if (child == lastParent) continue;
      if (!child.processDeclarations(processor, substitutor, null, place)) {
        return false;
      }
    }

    final String fileName = getName();
    if (!fileName.equals("__builtin__.py")) {
      final PyFile builtins = PyBuiltinCache.getInstance(getProject()).getBuiltinsFile();
      if (builtins != null && !builtins.processDeclarations(processor, substitutor, null, place)) return false;
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

  public List<PyClass> getTopLevelClasses() {
    List<PyClass> result = new ArrayList<PyClass>();
    final StubElement stub = getStub();
    if (stub != null) {
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
        if (child instanceof PyClassStub) {
          result.add(((PyClassStub)child).getPsi());
        }
      }
    }
    else {
      for (PsiElement child : getChildren()) {
        if (child instanceof PyClass) {
          result.add((PyClass)child);
        }
      }
    }

    return result;
  }

  public List<PyFunction> getTopLevelFunctions() {
    List<PyFunction> result = new ArrayList<PyFunction>();
    final StubElement stub = getStub();
    if (stub != null) {
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
        if (child instanceof PyFunctionStub) {
          result.add(((PyFunctionStub)child).getPsi());
        }
      }
    }
    else {
      for (PsiElement child : getChildren()) {
        if (child instanceof PyFunction) {
          result.add((PyFunction)child);
        }
      }
    }
    
    return result;
  }
}
