package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.codeInsight.CodeInsightColors;

import java.util.ArrayList;

public class JavaFileTreeElement extends PsiTreeElementBase<PsiJavaFile> implements ItemPresentation {

  public JavaFileTreeElement(PsiJavaFile file) {
    super(file);
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  public StructureViewTreeElement[] getChildrenBase() {
    PsiClass[] classes = getElement().getClasses();
    ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    for (int i = 0; i < classes.length; i++) {
      PsiClass aClass = classes[i];
      result.add(new JavaClassTreeElement(aClass, false));
    }
    return result.toArray(new StructureViewTreeElement[result.size()]);

  }

  public TextAttributesKey getTextAttributesKey() {
    return null;
  }
}
