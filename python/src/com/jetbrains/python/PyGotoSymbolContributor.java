// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;


public class PyGotoSymbolContributor implements GotoClassContributor, ChooseByNameContributorEx, PossiblyDumbAware {
  @Override
  public void processNames(@NotNull final Processor<? super String> processor, @NotNull final GlobalSearchScope scope, @Nullable final IdFilter filter) {
    FileBasedIndex fileIndex = FileBasedIndex.getInstance();
    StubIndex stubIndex = StubIndex.getInstance();
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      if (!fileIndex.processAllKeys(PyModuleNameIndex.NAME, processor, scope, filter)) return;
      if (!stubIndex.processAllKeys(PyClassNameIndex.KEY, processor, scope, filter)) return;
      if (!stubIndex.processAllKeys(PyFunctionNameIndex.KEY, processor, scope, filter)) return;
      if (!stubIndex.processAllKeys(PyVariableNameIndex.KEY, processor, scope, filter)) return;
      if (!stubIndex.processAllKeys(PyClassAttributesIndex.KEY, processor, scope, filter)) return;
    });
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    Project project = parameters.getProject();
    GlobalSearchScope scope = PySearchUtilBase.excludeSdkTestScope(parameters.getSearchScope());
    IdFilter filter = parameters.getIdFilter();
    FileBasedIndex fileIndex = FileBasedIndex.getInstance();
    StubIndex stubIndex = StubIndex.getInstance();
    PsiManager psiManager = PsiManager.getInstance(project);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      if (!fileIndex.getFilesWithKey(PyModuleNameIndex.NAME, Collections.singleton(name), file -> {
        if (PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(file)) return true;
        PsiFile psiFile = psiManager.findFile(file);
        return !(psiFile instanceof PyFile) || processor.process(psiFile);
      }, scope)) return;
      if (!stubIndex.processElements(PyClassNameIndex.KEY, name, project, scope, filter, PyClass.class, processor)) return;
      if (!stubIndex.processElements(PyFunctionNameIndex.KEY, name, project, scope, filter, PyFunction.class, processor)) return;
      if (!stubIndex.processElements(PyVariableNameIndex.KEY, name, project, scope, filter, PyTargetExpression.class, processor)) return;
      PyClassAttributesIndex.findClassAndInstanceAttributes(name, project, scope).forEach(processor::process);
    });
  }

  @Override
  public String getQualifiedName(@NotNull NavigationItem item) {
    if (item instanceof PyQualifiedNameOwner) {
      return ((PyQualifiedNameOwner) item).getQualifiedName();
    }
    return null;
  }

  @Override
  public String getQualifiedNameSeparator() {
    return ".";
  }

  @Override
  public boolean isDumbAware() {
    return FileBasedIndex.isIndexAccessDuringDumbModeEnabled();
  }
}
