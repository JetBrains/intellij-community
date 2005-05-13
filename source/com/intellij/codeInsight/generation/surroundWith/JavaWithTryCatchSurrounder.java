package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.util.IncorrectOperationException;

public class JavaWithTryCatchSurrounder extends JavaStatementsSurrounder {
  protected boolean myGenerateFinally = false;

  public String getTemplateDescription() {
    return "try / catch";
  }

  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements)
    throws IncorrectOperationException {
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = manager.getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements.length == 0) {
      return null;
    }

    PsiClassType[] exceptions = ExceptionUtil.getUnhandledExceptions(statements);
    if (exceptions.length == 0) {
      exceptions = ExceptionUtil.getThrownExceptions(statements);
      if (exceptions.length == 0) {
        exceptions = new PsiClassType[]{factory.createTypeByFQClassName("java.lang.Exception", container.getResolveScope())};
      }
    }

    StringBuffer buffer = new StringBuffer("try{\n}");
    for (PsiClassType exception : exceptions) {
      buffer.append("catch(Exception e){\n}");
    }
    if (myGenerateFinally) {
      buffer.append("finally{\n}");
    }
    String text = buffer.toString();
    PsiTryStatement tryStatement = (PsiTryStatement)factory.createStatementFromText(text, null);
    tryStatement = (PsiTryStatement)codeStyleManager.reformat(tryStatement);

    tryStatement = (PsiTryStatement)container.addAfter(tryStatement, statements[statements.length - 1]);

    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    tryBlock.addRange(statements[0], statements[statements.length - 1]);

    PsiCatchSection[] catchSections = tryStatement.getCatchSections();

    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exception = exceptions[i];
      String[] nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, exception)
        .names;
      String name = codeStyleManager.suggestUniqueVariableName(nameSuggestions[0], tryBlock, false);
      PsiCatchSection catchSection;
      try {
        catchSection = factory.createCatchSection(exception, name, null);
      }
      catch (IncorrectOperationException e) {
        Messages.showErrorDialog(project, "Invalid File Template for Catch Body!", "Surround With Try / Catch");
        return null;
      }
      catchSection = (PsiCatchSection)catchSections[i].replace(catchSection);
      codeStyleManager.shortenClassReferences(catchSection);
    }

    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    PsiCodeBlock firstCatch = tryStatement.getCatchBlocks()[0];
    return SurroundWithUtil.getRangeToSelect(firstCatch);
  }
}