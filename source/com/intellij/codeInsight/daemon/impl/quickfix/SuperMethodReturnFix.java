package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;

import java.text.MessageFormat;

public class SuperMethodReturnFix implements IntentionAction {

  private final PsiType mySuperMethodType;
  private final PsiMethod mySuperMethod;

  public SuperMethodReturnFix(PsiMethod superMethod, PsiType superMethodType) {
    mySuperMethodType = superMethodType;
    mySuperMethod = superMethod;
  }

  public String getText() {
    String name = PsiFormatUtil.formatMethod(
            mySuperMethod,
            PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS,
            0
    );
    String text = MessageFormat.format("Make ''{0}'' return ''{1}''",
            new Object[]{
              name,
              HighlightUtil.formatType(mySuperMethodType),
            }
    );
    return text;
  }

  public String getFamilyName() {
    return "Fix Super Method Return Type";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
            mySuperMethod != null
            && mySuperMethod.isValid()
            && mySuperMethod.getManager().isInProject(mySuperMethod)
            && mySuperMethodType != null
            && mySuperMethodType.isValid();
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(mySuperMethod.getContainingFile())) return;
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(
            project,
            mySuperMethod,
            false, null,
            mySuperMethod.getName(),
            mySuperMethodType,
            ParameterInfo.fromMethod(mySuperMethod));
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      processor.run();
    } else {
      processor.run();
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
