// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider,
                                                                            StructureViewModel.ExpandInfoProvider {
  public PyStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
    this(psiFile, editor, new PyStructureViewElement((PyElement) psiFile));
    withSorters(Sorter.ALPHA_SORTER);
    withSuitableClasses(PyFunction.class, PyClass.class);
  }

  public PyStructureViewModel(@NotNull PsiFile file, @Nullable Editor editor, @NotNull StructureViewTreeElement element) {
    super(file, editor, element);
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    final Object value = element.getValue();
    return value instanceof PyFile || value instanceof PyClass;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return element.getValue() instanceof PyTargetExpression;
  }

  @Override
  public boolean shouldEnterElement(Object element) {
    return element instanceof PyClass;
  }

  @Override
  public Filter @NotNull [] getFilters() {
    return new Filter[] {
      new PyInheritedMembersFilter(),
      new PyFieldsFilter(),
    };
  }

  @Override
  public boolean isAutoExpand(@NotNull StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isSmartExpand() {
    return false;
  }

  @Override
  public int getMinimumAutoExpandDepth() {
    return 1;
  }
}
