package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;

import javax.swing.*;
import java.util.ArrayList;

public class JavaFileTreeElement implements StructureViewTreeElement, ItemPresentation {

  private final PsiJavaFile myFile;

  public JavaFileTreeElement(PsiJavaFile file) {
    myFile = file;
  }

  public StructureViewTreeElement[] getChildren() {
    PsiClass[] classes = myFile.getClasses();
    ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    for (int i = 0; i < classes.length; i++) {
      PsiClass aClass = classes[i];
      result.add(new JavaClassTreeElement(aClass, false));
    }
    return result.toArray(new StructureViewTreeElement[result.size()]);
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public Icon getIcon(boolean open) {
    return myFile.getVirtualFile().getFileType().getIcon();
  }

  public String getLocationString() {
    return null;
  }

  public String getPresentableText() {
    return myFile.getName();
  }

  public Object getValue() {
    return myFile;
  }

  public String toString() {
    return getPresentableText();
  }
}
