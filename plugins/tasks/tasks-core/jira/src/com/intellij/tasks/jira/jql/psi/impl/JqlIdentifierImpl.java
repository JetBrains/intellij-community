package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlIdentifier;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlIdentifierImpl extends JqlElementImpl implements JqlIdentifier {
  public JqlIdentifierImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlIdentifier(this);
  }

  @Override
  public boolean isCustomField() {
    return findChildByType(JqlTokenTypes.CUSTOM_FIELD) != null;
  }

  @Override
  public String getEscapedText() {
    return unescape(getText());
  }
}
