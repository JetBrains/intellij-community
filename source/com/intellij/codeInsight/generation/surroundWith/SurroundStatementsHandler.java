
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.psi.*;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

interface SurroundStatementsHandler{
  /**
   * @return range to select/to position the caret
   */
  TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException;
}