package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlArgumentList;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlLiteral;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlArgumentListImpl extends JqlElementImpl implements JqlArgumentList {
  public JqlArgumentListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlArgumentList(this);
  }

  @Override
  public JqlLiteral @NotNull [] getArguments() {
    return findChildrenByClass(JqlLiteral.class);
  }
}
