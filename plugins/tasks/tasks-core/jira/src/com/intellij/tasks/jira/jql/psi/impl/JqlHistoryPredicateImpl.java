package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlHistoryPredicate;
import com.intellij.tasks.jira.jql.psi.JqlOperand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JqlHistoryPredicateImpl extends JqlElementImpl implements JqlHistoryPredicate {
  public JqlHistoryPredicateImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlHistoryPredicate(this);
  }

  @NotNull
  @Override
  public Type getType() {
    PsiElement keyword = findChildByType(JqlTokenTypes.HISTORY_PREDICATES);
    assert keyword != null;
    return Type.valueOf(keyword.getText().toUpperCase());
  }

  @Nullable
  @Override
  public JqlOperand getOperand() {
    return findChildByClass(JqlOperand.class);
  }
}
