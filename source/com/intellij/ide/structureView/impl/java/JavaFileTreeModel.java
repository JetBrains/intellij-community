package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;

public class JavaFileTreeModel implements StructureViewModel {
  private final PsiJavaFile myFile;

  public JavaFileTreeModel(PsiJavaFile file) {
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
    return new Sorter[]{KindSorter.INSTANCE, Sorter.ALPHA_SORTER};
  }

  public Object getCurrentEditorElement() {
    final Editor editor = FileEditorManager.getInstance(myFile.getProject()).getSelectedTextEditor();
    final Document document = FileDocumentManager.getInstance().getDocument(myFile.getVirtualFile());
    if (!Comparing.equal(editor.getDocument(), document)) return null;

    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = myFile.findElementAt(offset);
    while (!isSutable(element)) {
      if (element == null) return null;
      element = element.getParent();
    }
    return element;
  }

  private boolean isSutable(final PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField;
  }
}
