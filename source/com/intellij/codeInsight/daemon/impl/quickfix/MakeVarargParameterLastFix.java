package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiParameter;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

/**
 * @author ven
 */
public class MakeVarargParameterLastFix implements IntentionAction {
  public MakeVarargParameterLastFix(PsiParameter parameter) {
    myParameter = parameter;
  }

  private PsiParameter myParameter;

  public String getText() {
    return MessageFormat.format("Move ''{0}'' to the end of the list", new Object[]{myParameter.getName()});
  }

  public String getFamilyName() {
    return "Make vararg parameter last";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myParameter.isValid() && myParameter.getManager().isInProject(myParameter);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myParameter.getParent().add(myParameter);
    myParameter.delete();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
