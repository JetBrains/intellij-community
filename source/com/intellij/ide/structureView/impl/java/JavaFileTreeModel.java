package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class JavaFileTreeModel extends TextEditorBasedStructureViewModel {
  private final PsiJavaFile myFile;

  public JavaFileTreeModel(@NotNull PsiJavaFile file) {
    super(file);
    myFile = file;
  }

  @NotNull
  public Filter[] getFilters() {
    return new Filter[]{new InheritedMembersFilter(),
                        new FieldsFilter(),
                        new PublicElementsFilter()};
  }

  @NotNull
  public Grouper[] getGroupers() {
    return new Grouper[]{new SuperTypesGrouper(), new PropertiesGrouper()};
  }

  @NotNull
  public StructureViewTreeElement<PsiJavaFile> getRoot() {
    return new JavaFileTreeElement(myFile);
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[]{KindSorter.INSTANCE, VisibilitySorter.INSTANCE, Sorter.ALPHA_SORTER};
  }

  protected PsiFile getPsiFile() {
    return myFile;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
    return new Class[]{PsiClass.class, PsiMethod.class, PsiField.class, PsiJavaFile.class};
  }
}
