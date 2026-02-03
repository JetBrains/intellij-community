// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull String getFieldName() {
    return getField().getText();
  }

  @Override
  public @NotNull JqlIdentifier getField() {
    JqlIdentifier identifier = findChildByClass(JqlIdentifier.class);
    assert identifier != null;
    return identifier;
  }
}
