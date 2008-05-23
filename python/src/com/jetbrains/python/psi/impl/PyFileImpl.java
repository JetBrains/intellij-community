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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
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
    for(PyClass c: getTopLevelClasses()) {
      if (c == lastParent) continue;
      if (!processor.execute(c, substitutor)) return false;
    }
    for(PyFunction f: getTopLevelFunctions()) {
      if (f == lastParent) continue;
      if (!processor.execute(f, substitutor)) return false;
    }
    for(PyTargetExpression e: getTopLevelAttributes()) {
      if (e == lastParent) continue;
      if (!processor.execute(e, substitutor)) return false;
    }

    for(PyExpression e: getImportTargets()) {
      if (e == lastParent) continue;
      if (!processor.execute(e, substitutor)) return false;
    }

    for(PyFromImportStatement e: getFromImports()) {
      if (e == lastParent) continue;
      if (!e.processDeclarations(processor, substitutor, null, this)) return false;
    }

    // if we're in a stmt (not place itself), try buitins:
    if (lastParent != null) {
      final String fileName = getName();
      if (!fileName.equals("__builtin__.py")) {
        final PyFile builtins = PyBuiltinCache.getInstance(getProject()).getBuiltinsFile();
        if (builtins != null && !builtins.processDeclarations(processor, substitutor, null, place)) return false;
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

  public List<PyClass> getTopLevelClasses() {
    return getTopLevelItems(PyElementTypes.CLASS_DECLARATION, PyClass.class);
  }

  public List<PyFunction> getTopLevelFunctions() {
    return getTopLevelItems(PyElementTypes.FUNCTION_DECLARATION, PyFunction.class);
  }

  public List<PyTargetExpression> getTopLevelAttributes() {
    return getTopLevelItems(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.class);
  }

  public List<PyExpression> getImportTargets() {
    List<PyExpression> ret = new ArrayList<PyExpression>();
    List<PyImportStatement> imports = getTopLevelItems(PyElementTypes.IMPORT_STATEMENT, PyImportStatement.class);
    for (PyImportStatement one: imports) {
      for (PyImportElement elt: one.getImportElements()) {
        PyExpression target = elt.getAsName();
        if (target != null) ret.add(target);
        else {
          target = elt.getImportReference();
          if (target != null) ret.add(target);
        }
      }
    }
    return ret;
  }
  
  public List<PyFromImportStatement> getFromImports() {
    final List<PyFromImportStatement> result = new ArrayList<PyFromImportStatement>();
    accept(new PyRecursiveElementVisitor() {
      public void visitPyElement(final PyElement node) {
        super.visitPyElement(node);
        if (PyFromImportStatement.class.isInstance(node)) {
          //noinspection unchecked
          result.add((PyFromImportStatement)node);
        }
      }
    });
    return result;
  }

  private <T> List<T> getTopLevelItems(final IElementType elementType, final Class itemClass) {
    final List<T> result = new ArrayList<T>();
    final StubElement stub = getStub();
    if (stub != null) {
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
        if (child.getStubType() == elementType) {
          //noinspection unchecked
          result.add((T)child.getPsi());
        }
      }
    }
    else {
      accept(new PyRecursiveElementVisitor() {
        public void visitPyElement(final PyElement node) {
          super.visitPyElement(node);
          checkAddElement(node);
        }

        public void visitPyClass(final PyClass node) {
          checkAddElement(node);  // do not recurse into functions
        }

        public void visitPyFunction(final PyFunction node) {
          checkAddElement(node);  // do not recurse into classes
        }

        private void checkAddElement(PsiElement node) {
          if (itemClass.isInstance(node)) {
            //noinspection unchecked
            result.add((T)node);
          }
        }
      });
    }
    return result;
  }
}
