package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider {
  public PyStructureViewModel(@NotNull PsiFile psiFile) {
    super(psiFile, new PyStructureViewElement((PyElement) psiFile));
    withSorters(Sorter.ALPHA_SORTER);
    withSuitableClasses(PyFunction.class, PyClass.class);
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

  @NotNull
  @Override
  public Filter[] getFilters() {
    return new Filter[] {
      new PyInheritedMembersFilter(),
    };
  }

  @Override
  public boolean isAutoExpand(StructureViewTreeElement element) {
    return element.getValue() instanceof PsiFile;
  }

  @Override
  public boolean isSmartExpand() {
    return false;
  }
}
