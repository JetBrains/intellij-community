package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlIdentifier;
import com.intellij.tasks.jira.jql.psi.JqlTerminalClause;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class JqlTerminalClauseImpl extends JqlElementImpl implements JqlTerminalClause {
  public JqlTerminalClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public String getFieldName() {
    return getField().getText();
  }

  @NotNull
  @Override
  public JqlIdentifier getField() {
    JqlIdentifier identifier = findChildByClass(JqlIdentifier.class);
    assert identifier != null;
    return identifier;
  }
}
