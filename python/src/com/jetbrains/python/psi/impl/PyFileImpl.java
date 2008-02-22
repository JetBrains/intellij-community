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
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PythonLanguage;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA. User: yole Date: 28.05.2005 Time: 10:08:07 To
 * change this template use File | Settings | File Templates.
 */
public class PyFileImpl extends PsiFileBase implements PyFile {
    private final FileType fileType;
    public PyFileImpl(FileViewProvider viewProvider, PythonLanguage language,
                      FileType pythonFileType) {
        super(viewProvider, language);
        this.fileType = pythonFileType;
    }

    @NotNull
    public FileType getFileType() {
        return fileType;
    }

    public String toString() {
        return "PyFile:" + getName();
    }

    public Icon getIcon(int i) {
        return fileType.getIcon();
    }

    public void accept(PsiElementVisitor visitor) {
        if (visitor instanceof PyElementVisitor) {
            ((PyElementVisitor) visitor).visitPyFile(this);
        } else {
            super.accept(visitor);
        }
    }

    public boolean processDeclarations(PsiScopeProcessor processor,
                                       ResolveState substitutor,
                                       PsiElement lastParent,
                                       PsiElement place) {
        final PsiElement[] children = getChildren();
        for (PsiElement child : children) {
            if (!child.processDeclarations(processor, substitutor, lastParent,
                    place)) {
                return false;
            }
        }
        return true;
    }

    @Nullable public <T extends PyElement> T getContainingElement(
            Class<T> aClass) {
        return null;
    }

    public @Nullable PyElement getContainingElement(TokenSet tokenSet) {
      return null;
    }

    @PsiCached
    public List<PyStatement> getStatements() {
        List<PyStatement> stmts = new ArrayList<PyStatement>();
        for (PsiElement child : getChildren()) {
            if (child instanceof PyStatement) {
                PyStatement statement = (PyStatement) child;
                stmts.add(statement);
            }
        }
        return stmts;
    }

  public PythonLanguage getPyLanguage() {
    return (PythonLanguage) getLanguage();
  }
}
