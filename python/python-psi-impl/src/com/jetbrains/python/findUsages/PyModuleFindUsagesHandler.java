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
package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyImportedModule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
public class PyModuleFindUsagesHandler extends PyFindUsagesHandler {
  final PsiFileSystemItem myElement;

  protected PyModuleFindUsagesHandler(@NotNull PsiFileSystemItem file) {
    super(file);
    final PsiElement e = PyUtil.turnInitIntoDir(file);
    myElement = e instanceof PsiFileSystemItem ? (PsiFileSystemItem)e : file;
  }

  @Override
  public PsiElement @NotNull [] getPrimaryElements() {
    return new PsiElement[] {myElement};
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferencesToHighlight(@NotNull PsiElement target, @NotNull SearchScope searchScope) {
    if (target instanceof PyImportedModule) {
      target = ((PyImportedModule) target).resolve();
    }
    if (target instanceof PyFile && PyNames.INIT_DOT_PY.equals(((PyFile)target).getName())) {
      List<PsiReference> result = new ArrayList<>(super.findReferencesToHighlight(target, searchScope));
      PsiElement targetDir = PyUtil.turnInitIntoDir(target);
      if (targetDir != null) {
        result.addAll(ReferencesSearch.search(targetDir, searchScope, false).findAll());
      }
      return result;
    }
    return super.findReferencesToHighlight(target, searchScope);
  }
}
