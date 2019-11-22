// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.Language;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * @author yole
 */
public class PyGotoClassContributor implements GotoClassContributor, ChooseByNameContributorEx {
  @Override
  public void processNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    if (!StubIndex.getInstance().processAllKeys(PyClassNameIndex.KEY, processor, scope, filter)) return;
    if (!FileBasedIndex.getInstance().processAllKeys(PyModuleNameIndex.NAME, processor, scope, filter)) return;
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    Project project = parameters.getProject();
    GlobalSearchScope scope = PySearchUtilBase.excludeSdkTestScope(parameters.getSearchScope());
    IdFilter filter = parameters.getIdFilter();
    PsiManager psiManager = PsiManager.getInstance(project);
    if (!StubIndex.getInstance().processElements(PyClassNameIndex.KEY, name, project, scope, filter, PyClass.class, processor)) return;
    if (!FileBasedIndex.getInstance().getFilesWithKey(PyModuleNameIndex.NAME, Collections.singleton(name), file -> {
      if (PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(file)) return true;
      PsiFile psiFile = psiManager.findFile(file);
      return !(psiFile instanceof PyFile) || processor.process(psiFile);
    }, scope)) return;
  }

  @Nullable
  @Override
  public String getQualifiedName(NavigationItem item) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedNameSeparator() {
    return null;
  }

  @Nullable
  @Override
  public Language getElementLanguage() {
    return PythonLanguage.getInstance();
  }
}
