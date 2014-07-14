package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlLiteral;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlLiteralImpl extends JqlElementImpl implements JqlLiteral {
  public JqlLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlLiteral(this);
  }

  @Override
  public String getEscapedText() {
    return unescape(getText());
  }

  @Override
  public String getOriginalText() {
    return getNode().getText();
  }
}
