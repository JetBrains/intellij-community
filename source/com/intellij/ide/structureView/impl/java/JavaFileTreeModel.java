package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.*;

public class JavaFileTreeModel extends TextEditorBasedStructureViewModel {
  private final PsiJavaFile myFile;

  public JavaFileTreeModel(PsiJavaFile file) {
    super(file);
    myFile = file;
  }

  public Filter[] getFilters() {
    return new Filter[]{new InheritedMembersFilter(),
                        new FieldsFilter(),
                        new PublicElementsFilter()};
  }

  public Grouper[] getGroupers() {
    return new Grouper[]{new SuperTypesGrouper(), new PropertiesGrouper()};
  }

  public StructureViewTreeElement getRoot() {
    return new JavaFileTreeElement(myFile);
  }

  public Sorter[] getSorters() {
    return new Sorter[]{KindSorter.INSTANCE, Sorter.ALPHA_SORTER, VisibilitySorter.INSTANCE};
  }

  protected PsiFile getPsiFile() {
    return myFile;
  }

  protected Class[] getSuitableClasses() {
    return new Class[]{PsiClass.class, PsiMethod.class, PsiField.class};
  }
}
