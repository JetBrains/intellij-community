package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateEqualsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClass;

/**
 * @author dsl
 */
public class GenerateEqualsAction extends BaseGenerateAction {
  public GenerateEqualsAction() {
    super(new GenerateEqualsHandler());
  }

  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    final PsiClass targetClass = super.getTargetClass(editor, file);
    if (targetClass.isEnum()) return null;
    return targetClass;
  }
}
