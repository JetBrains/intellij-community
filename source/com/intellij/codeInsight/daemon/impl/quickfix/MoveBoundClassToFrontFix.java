package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class MoveBoundClassToFrontFix extends ExtendsListFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MoveBoundClassToFrontFix");

  public MoveBoundClassToFrontFix(PsiClass aClass, PsiClassType classToExtendFrom) {
    super(aClass, classToExtendFrom, true);
  }

  public String getText() {
    final String text = MessageFormat.format("Move bound ''{0}'' to the beginning of the bounds list of type parameter ''{1}''",
        new Object[]{
          HighlightUtil.formatClass(myClassToExtendFrom),
          HighlightUtil.formatClass(myClass),
        });
    return text;
  }

  public String getFamilyName() {
    return "Move Class in Extend list";
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    PsiReferenceList extendsList = myClass.getExtendsList();
    if (extendsList == null) return;
    try {
      modifyList(extendsList, false, -1);
      modifyList(extendsList, true, 0);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    QuickFixAction.spoilDocument(project, file);
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        myClass != null
        && myClass.isValid()
        && myClass.getManager().isInProject(myClass)
        && myClassToExtendFrom != null
        && myClassToExtendFrom.isValid()
    ;
  }
}
