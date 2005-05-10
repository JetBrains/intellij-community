package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.impl.analysis.ClassUtil;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.ImplementMethodsHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.MethodSignatureUtil;

/**
 *
 */
public class ImplementMethodsAction extends BaseCodeInsightAction {

  protected CodeInsightActionHandler getHandler() {
    return new ImplementMethodsHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    if (!file.canContainJavaCode()) {
      return false;
    }

    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file);
    if (aClass == null) {
      return false;
    }
    final MethodSignatureUtil.MethodSignatureToMethods allMethods = MethodSignatureUtil.getOverrideEquivalentMethods(aClass);
    return ClassUtil.getAnyMethodToImplement(aClass, allMethods) != null;
  }
}