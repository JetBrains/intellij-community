package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents an argument list of a function call.
 */
@ApiStatus.Experimental
public interface PyAstArgumentList extends PyAstElement {

  /**
   * @return all argument list param expressions (keyword argument or nameless)
   */
  @NotNull
  default Collection<? extends PyAstExpression> getArgumentExpressions() {
    final PyAstExpression[] arguments = getArguments();
    final Collection<PyAstExpression> result = new ArrayList<>(arguments.length);
    for (final PyAstExpression expression : arguments) {
      if (expression instanceof PyAstKeywordArgument) {
        final PyAstExpression valueExpression = ((PyAstKeywordArgument)expression).getValueExpression();
        result.add(valueExpression);
      }
      if (expression instanceof PyAstReferenceExpression) {
        result.add(expression);
      }
    }
    return result;
  }

  default PyAstExpression @NotNull [] getArguments() {
    return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), PyAstExpression.EMPTY_ARRAY);
  }

  @Nullable
  default PyAstKeywordArgument getKeywordArgument(String name) {
    ASTNode node = getNode().getFirstChildNode();
    while (node != null) {
      if (node.getElementType() == PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION) {
        PyAstKeywordArgument arg = (PyAstKeywordArgument)node.getPsi();
        String keyword = arg.getKeyword();
        if (keyword != null && keyword.equals(name)) return arg;
      }
      node = node.getTreeNext();
    }
    return null;
  }

  /**
   * @return the call expression to which this argument list belongs; not null in correctly parsed cases.
   */
  @Nullable
  default PyAstCallExpression getCallExpression() {
    return PsiTreeUtil.getParentOfType(this, PyAstCallExpression.class);
  }

  @Nullable
  default ASTNode getClosingParen() {
    ASTNode node = getNode();
    final ASTNode[] children = node.getChildren(TokenSet.create(PyTokenTypes.RPAR));
    return children.length == 0 ? null : children[children.length - 1];
  }
}
