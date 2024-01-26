// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.Language;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;


public class PyGotoClassContributor implements GotoClassContributor, ChooseByNameContributorEx, PossiblyDumbAware {
  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      if (!StubIndex.getInstance().processAllKeys(PyClassNameIndex.KEY, processor, scope, filter)) return;
      FileBasedIndex.getInstance().processAllKeys(PyModuleNameIndex.NAME, processor, scope, filter);
    });
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    Project project = parameters.getProject();
    GlobalSearchScope scope = PySearchUtilBase.excludeSdkTestScope(parameters.getSearchScope());
    IdFilter filter = parameters.getIdFilter();
    PsiManager psiManager = PsiManager.getInstance(project);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      if (!StubIndex.getInstance().processElements(PyClassNameIndex.KEY, name, project, scope, filter, PyClass.class, processor)) return;
      FileBasedIndex.getInstance().getFilesWithKey(PyModuleNameIndex.NAME, Collections.singleton(name), file -> {
        if (PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(file)) return true;
        PsiFile psiFile = psiManager.findFile(file);
        return !(psiFile instanceof PyFile) || processor.process(psiFile);
      }, scope);
    });
  }

  @Nullable
  @Override
  public String getQualifiedName(@NotNull NavigationItem item) {
    return item instanceof PyQualifiedNameOwner qNameOwner ? qNameOwner.getQualifiedName() : null;
  }

  @Nullable
  @Override
  public String getQualifiedNameSeparator() {
    return ".";
  }

  @Nullable
  @Override
  public Language getElementLanguage() {
    return PythonLanguage.getInstance();
  }

  @Override
  public boolean isDumbAware() {
    return FileBasedIndex.isIndexAccessDuringDumbModeEnabled();
  }
}
