package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class DeleteCatchFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix");

  private final PsiParameter myCatchParameter;

  public DeleteCatchFix(PsiParameter myCatchParameter) {
    this.myCatchParameter = myCatchParameter;
  }

  public String getText() {
    final String text = MessageFormat.format("Delete catch for ''{0}''",
        new Object[]{
          HighlightUtil.formatType(myCatchParameter.getType()),
        });
    return text;
  }

  public String getFamilyName() {
    return "Delete Catch";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiElement element = myCatchParameter.getContainingFile();
    return myCatchParameter != null
        && myCatchParameter.isValid()
        && element.getManager().isInProject(element);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myCatchParameter.getContainingFile())) return;
    try {
      PsiTryStatement tryStatement = (PsiTryStatement) myCatchParameter.getDeclarationScope();
      final PsiElement tryParent = tryStatement.getParent();
      if (tryStatement.getCatchBlocks().length == 1 && tryStatement.getFinallyBlock() == null) {
        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        PsiElement reformatRangeStart = tryStatement.getPrevSibling();
        PsiElement reformatRangeEnd = tryStatement.getNextSibling();
        while((reformatRangeEnd = reformatRangeEnd.getNextSibling()) instanceof PsiWhiteSpace);
        if(reformatRangeEnd == null) reformatRangeEnd = tryParent;
        PsiElement insideElement = tryBlock.getLBrace().getNextSibling();
        if (insideElement != tryBlock.getRBrace()) {
          PsiElement endElement = tryBlock.getRBrace().getPrevSibling();

          reformatRangeStart = tryParent.addRangeBefore(insideElement, endElement, tryStatement).getPrevSibling();
        }
        tryStatement.delete();
        CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
        styleManager.reformatRange(tryParent, reformatRangeStart.getTextRange().getEndOffset(), reformatRangeEnd.getTextRange().getStartOffset());
        return;
      }

      // delete catch section
      LOG.assertTrue(myCatchParameter.getParent() instanceof PsiCatchSection);
      myCatchParameter.getParent().delete();
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
