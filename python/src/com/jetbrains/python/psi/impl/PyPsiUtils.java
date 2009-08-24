/*
 * @author max
 */
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyPsiUtils {
  public static final Key<Pair<PsiElement, TextRange>> SELECTION_BREAKS_AST_NODE =
    new Key<Pair<PsiElement, TextRange>>("python.selection.breaks.ast.node");

  private PyPsiUtils() {
  }

  protected static <T extends PyElement> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
    T[] psiElements = (T[])java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      //noinspection unchecked
      psiElements[i] = (T)nodes[i].getPsi();
    }
    return psiElements;
  }

  @Nullable
  protected static ASTNode getPrevComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreePrev();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }

  @Nullable
  protected static ASTNode getNextComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }

  public static void replaceExpression(@NotNull final Project project,
                                       @NotNull final PsiElement oldExpression,
                                       @NotNull final PsiElement newExpression) {
    final Pair<PsiElement, TextRange> data = oldExpression.getUserData(SELECTION_BREAKS_AST_NODE);
    if (data != null) {
      final PsiElement parent = data.first;
      final TextRange textRange = data.second;
      final String parentText = parent.getText();
      final String prefix = parentText.substring(0, textRange.getStartOffset());
      final String suffix = parentText.substring(textRange.getEndOffset(), parent.getTextLength());
      final PsiElement expression = PythonLanguage.getInstance().getElementGenerator()
        .createFromText(project, parent.getClass(), prefix + newExpression.getText() + suffix);
      parent.replace(expression);
    }
    else {
      oldExpression.replace(newExpression);
    }
  }
}