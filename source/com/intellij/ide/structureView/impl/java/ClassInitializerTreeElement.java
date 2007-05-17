package com.intellij.ide.structureView.impl.java;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class ClassInitializerTreeElement extends PsiTreeElementBase<PsiClassInitializer> {
  public ClassInitializerTreeElement(PsiClassInitializer initializer) {
    super(initializer);
  }

  public String getPresentableText() {
    PsiClassInitializer initializer = getElement();
    assert initializer != null;
    String isStatic = initializer.hasModifierProperty(PsiModifier.STATIC) ? PsiModifier.STATIC + " " : "";
    return CodeInsightBundle.message("static.class.initializer", isStatic);
  }

  public String getLocationString() {
    PsiClassInitializer initializer = getElement();
    assert initializer != null;
    PsiCodeBlock body = initializer.getBody();
    PsiElement first = body.getFirstBodyElement();
    if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
    if (first == body.getRBrace()) first = null;
    if (first != null) {
      return StringUtil.first(first.getText(), 20, true);
    }
    return null;
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }

  public TextAttributesKey getTextAttributesKey() {
    return null;
  }
}