package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlArgumentList;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlFunctionCall;
import com.intellij.tasks.jira.jql.psi.JqlIdentifier;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlFunctionCallImpl extends JqlElementImpl implements JqlFunctionCall {
  public JqlFunctionCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlFunctionCall(this);
  }

  @NotNull
  @Override
  public JqlIdentifier getFunctionName() {
    JqlIdentifier idenifier = findChildByClass(JqlIdentifier.class);
    assert idenifier != null;
    return idenifier;
  }

  @NotNull
  @Override
  public JqlArgumentList getArgumentList() {
    return findChildByClass(JqlArgumentList.class);
  }
}
