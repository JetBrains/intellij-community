package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateEqualsHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

/**
 * @author dsl
 */
public class GenerateEqualsAction extends BaseGenerateAction {
  public GenerateEqualsAction() {
    super(new GenerateEqualsHandler());
  }

  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    final PsiClass targetClass = super.getTargetClass(editor, file);
    if (targetClass == null || targetClass instanceof PsiAnonymousClass ||
        targetClass.isEnum()) return null;
    return targetClass;
  }
}
