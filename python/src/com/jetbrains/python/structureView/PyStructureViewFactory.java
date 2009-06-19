package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyStructureViewFactory implements PsiStructureViewFactory {
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {

      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new StructureViewModelBase(psiFile, new PyStructureViewElement((PyElement) psiFile))
          .withSorters(Sorter.ALPHA_SORTER)
          .withSuitableClasses(PyFunction.class, PyClass.class);
      }
    };
  }
}
