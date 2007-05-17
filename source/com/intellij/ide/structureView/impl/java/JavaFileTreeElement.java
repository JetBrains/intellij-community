package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;

import java.util.ArrayList;
import java.util.Collection;

import org.jetbrains.annotations.NotNull;

public class JavaFileTreeElement extends PsiTreeElementBase<PsiJavaFile> implements ItemPresentation {

  public JavaFileTreeElement(PsiJavaFile file) {
    super(file);
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    PsiClass[] classes = getElement().getClasses();
    ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    for (PsiClass aClass : classes) {
      result.add(new JavaClassTreeElement(aClass, false));
    }
    return result;

  }

  public TextAttributesKey getTextAttributesKey() {
    return null;
  }
}