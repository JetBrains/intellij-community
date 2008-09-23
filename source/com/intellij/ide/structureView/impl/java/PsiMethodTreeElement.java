package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PsiMethodTreeElement extends JavaClassTreeElementBase<PsiMethod> implements SortableTreeElement {
  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited,method);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    final PsiMethod element = getElement();
    if (element == null || element instanceof JspHolderMethod) return result;

    final TextRange range = element.getTextRange();
    if (range == null) return result;

    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null || psiFile instanceof PsiCompiledElement) return result;

    final String fileText = psiFile.getText();
    if (fileText == null) return result;
    
    if (!fileText.substring(range.getStartOffset(), range.getEndOffset()).contains(PsiKeyword.CLASS)) return result;

    element.accept(new JavaRecursiveElementVisitor(){
      @Override public void visitClass(PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass) && !(aClass instanceof PsiTypeParameter)) {
          result.add(new JavaClassTreeElement(aClass, isInherited()));
        }

      }
    });
    return result;
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public String getPresentableText() {
    return PsiFormatUtil.formatMethod(
      getElement(),
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
  }


  public TextAttributesKey getTextAttributesKey() {
    if (isInherited()) return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    return super.getTextAttributesKey();
  }

  public PsiMethod getMethod() {
    return getElement();
  }

  public String getAlphaSortKey() {
    final PsiMethod method = getElement();
    if (method != null) {
      return method.getName();
    }
    return "";
  }
}
