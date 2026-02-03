package com.intellij.tasks.jira.jql;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlElement extends ASTWrapperPsiElement {
  public JqlElement(@NotNull ASTNode node) {
    super(node);
  }
}
