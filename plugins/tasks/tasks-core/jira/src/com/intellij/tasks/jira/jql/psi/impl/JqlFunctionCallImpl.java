// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull JqlIdentifier getFunctionName() {
    JqlIdentifier idenifier = findChildByClass(JqlIdentifier.class);
    assert idenifier != null;
    return idenifier;
  }

  @Override
  public @NotNull JqlArgumentList getArgumentList() {
    return findChildByClass(JqlArgumentList.class);
  }
}
