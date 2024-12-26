// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlClause;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlSubClause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JqlSubClauseImpl extends JqlElementImpl implements JqlSubClause {
  public JqlSubClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlSubClause(this);
  }

  @Override
  public @Nullable JqlClause getInnerClause() {
    return findChildByClass(JqlClause.class);
  }
}
