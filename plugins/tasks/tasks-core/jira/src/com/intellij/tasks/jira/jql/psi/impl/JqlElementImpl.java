package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlElement;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class JqlElementImpl extends ASTWrapperPsiElement implements JqlElement {
  protected JqlElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    String className = getClass().getSimpleName();
    if (className.endsWith("Impl")) {
      className = className.substring(0, className.length() - 4);
    }
    return String.format("%s(%s)", className, getText());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JqlElementVisitor) {
      accept((JqlElementVisitor)visitor);
    }
    else {
      super.accept(visitor);
    }
  }

  protected static String unescape(String s) {
    if (s.length() >= 2 &&
        ((s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') ||
         (s.charAt(0) == '\"' && s.charAt(s.length() - 1) == '\"'))) {
      s = s.substring(1, s.length() - 1);
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c1 = s.charAt(i);
      if (c1 == '\\') {
        assert i != s.length() - 1 : "Trailing backslash";
        char c2 = s.charAt(++i);
        switch (c2) {
          case ' ':
          case '\'':
          case '\"':
          case '\\':
            builder.append(c2);
            break;
          case 'n':
            builder.append('\n');
            break;
          case 't':
            builder.append('\t');
            break;
          case '\r':
            builder.append('\r');
            break;
          // Only \\uXXXX escape is legal, so character always resides inside BMP
          case 'u':
            assert i < s.length() - 4 : "Incomplete unicode escape sequence: " + s.substring(i - 1);
            builder.append((char)Integer.parseInt(s.substring(i + 1, i + 5), 16));
            i += 4;
            break;
          default:
            throw new AssertionError("Illegal escape at " + s.substring(i));
        }
      }
      else {
        builder.append(c1);
      }
    }
    return builder.toString();
  }
  public abstract void accept(JqlElementVisitor visitor);
}
