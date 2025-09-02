// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 *
 * Helps to specify type by assertion
 */
public final class TypeAssertionIntention extends PyBaseIntentionAction {

  @Override
  public @NotNull String getText() {
    return PyPsiBundle.message("INTN.insert.assertion");
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.insert.assertion");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile)) {
      return false;
    }

    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(psiFile, editor.getCaretModel().getOffset());
    PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
    if (problemElement == null) return false;
    if (problemElement.getParent() instanceof PyWithItem) return false;
    final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
    if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
      problemElement = qualifier;
    }
    final PsiReference reference = problemElement.getReference();
    if (problemElement.getParent() instanceof PyCallExpression ||
        PsiTreeUtil.getParentOfType(problemElement, PyComprehensionElement.class) != null ||
        PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null ||
        PsiTreeUtil.getParentOfType(problemElement, PyGeneratorExpression.class) != null ||
        (reference != null && reference.resolve() == null)) {
      return false;
    }
    final PyType type = TypeEvalContext.codeAnalysis(psiFile.getProject(), psiFile).getType(problemElement);
    return type == null;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyQualifiedExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
    if (problemElement != null) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      String name = problemElement.getText();
      final PyExpression qualifier = problemElement.getQualifier();
      if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
        final String referencedName = problemElement.getReferencedName();
        if (referencedName == null || PyNames.GETITEM.equals(referencedName))
          name = qualifier.getText();
      }

      final String text = "assert isinstance(" + name + ", )";
      PyAssertStatement assertStatement = elementGenerator.createFromText(LanguageLevel.forElement(problemElement),
                                                                          PyAssertStatement.class, text);

      final PsiElement parentStatement = PsiTreeUtil.getParentOfType(problemElement, PyStatement.class);
      if (parentStatement == null) return;
      final PsiElement parent = parentStatement.getParent();
      PsiElement element;
      if (parentStatement instanceof PyAssignmentStatement &&
          ((PyAssignmentStatement)parentStatement).getTargets()[0] == problemElement) {
        element = parent.addAfter(assertStatement, parentStatement);
      }
      else {
        PyStatementList statementList = PsiTreeUtil.getParentOfType(parentStatement, PyStatementList.class);
        final Document document = editor.getDocument();

        if (statementList != null) {
          PsiElement statementListParent = PsiTreeUtil.getParentOfType(statementList, PyStatement.class);
          if (statementListParent != null && document.getLineNumber(statementList.getTextOffset()) ==
              document.getLineNumber(statementListParent.getTextOffset())) {
            final String substring =
              TextRange.create(statementListParent.getTextRange().getStartOffset(), statementList.getTextOffset()).substring(document.getText());
            final PyStatement foo =
              elementGenerator.createFromText(LanguageLevel.forElement(problemElement), PyStatement.class, substring + "\n\t" +
                                             text + "\n\t" + statementList.getText());

            statementListParent = statementListParent.replace(foo);
            statementList = PsiTreeUtil.findChildOfType(statementListParent, PyStatementList.class);
            assert statementList != null;
            element = statementList.getStatements()[0];
          }
          else
            element = parent.addBefore(assertStatement, parentStatement);
        }
        else {
          element = parent.addBefore(assertStatement, parentStatement);
        }
      }

      int textOffSet = element.getTextOffset();
      editor.getCaretModel().moveToOffset(textOffSet);

      element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(element);
      builder.replaceRange(TextRange.create(text.length()-1, text.length()-1), PyNames.OBJECT);
      builder.run(editor, true);
    }
  }
}
