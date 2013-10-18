package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actions.ConvertIndentsActionBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ConvertIndentsFix implements LocalQuickFix {
  private final boolean myToSpaces;

  public ConvertIndentsFix(boolean toSpaces) {
    myToSpaces = toSpaces;
  }

  @NotNull
  @Override
  public String getName() {
    return myToSpaces ? "Convert indents to spaces" : "Convert indents to tabs";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert indents";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiFile file = descriptor.getPsiElement().getContainingFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      int tabSize = CodeStyleFacade.getInstance(project).getIndentSize(file.getFileType());
      TextRange allDoc = new TextRange(0, document.getTextLength());
      if (myToSpaces) {
        ConvertIndentsActionBase.convertIndentsToSpaces(document, tabSize, allDoc);
      }
      else {
        ConvertIndentsActionBase.convertIndentsToTabs(document, tabSize, allDoc);
      }
    }
  }
}
