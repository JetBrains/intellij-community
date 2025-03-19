// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlChangedClause;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlChangedClauseImpl extends JqlClauseWithHistoryPredicatesImpl implements JqlChangedClause {
  public JqlChangedClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlChangedClause(this);
  }

  @Override
  public @NotNull Type getType() {
    return Type.CHANGED;
  }
}
