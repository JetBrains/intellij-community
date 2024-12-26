// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
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

  @Override
  public @NotNull Type getType() {
    PsiElement keyword = findChildByType(JqlTokenTypes.HISTORY_PREDICATES);
    assert keyword != null;
    return Type.valueOf(StringUtil.toUpperCase(keyword.getText()));
  }

  @Override
  public @Nullable JqlOperand getOperand() {
    return findChildByClass(JqlOperand.class);
  }
}
