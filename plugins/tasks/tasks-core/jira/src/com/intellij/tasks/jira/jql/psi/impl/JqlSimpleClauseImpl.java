package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlOperand;
import com.intellij.tasks.jira.jql.psi.JqlSimpleClause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JqlSimpleClauseImpl extends JqlTerminalClauseImpl implements JqlSimpleClause {
  public JqlSimpleClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlSimpleClause(this);
  }

  @NotNull
  @Override
  public Type getType() {
    if (findChildByType(JqlTokenTypes.IN_KEYWORD) != null) {
      return findChildByType(JqlTokenTypes.NOT_KEYWORD) != null ? Type.NOT_IN : Type.IN;
    }
    if (findChildByType(JqlTokenTypes.IS_KEYWORD) != null) {
      return findChildByType(JqlTokenTypes.NOT_KEYWORD) != null ? Type.IS_NOT : Type.IS;
    }
    PsiElement opElem = findChildByType(JqlTokenTypes.SIGN_OPERATORS);
    assert opElem != null;
    Type type = Type.fromTokenType(opElem.getNode().getElementType());
    assert type != null;
    return type;
  }

  /**
   * Operand can be missing in malformed query.
   */
  @Nullable
  @Override
  public JqlOperand getOperand() {
    return findChildByClass(JqlOperand.class);
  }
}
