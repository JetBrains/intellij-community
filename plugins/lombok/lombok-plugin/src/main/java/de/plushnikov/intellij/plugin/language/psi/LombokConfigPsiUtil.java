package de.plushnikov.intellij.plugin.language.psi;

import com.intellij.lang.ASTNode;

public class LombokConfigPsiUtil {
  public static String getKey(LombokConfigProperty element) {
    ASTNode keyNode = element.getNode().findChildByType(LombokConfigTypes.KEY);
    if (keyNode != null) {
      return keyNode.getText();
    } else {
      return null;
    }
  }

  public static String getValue(LombokConfigProperty element) {
    ASTNode valueNode = element.getNode().findChildByType(LombokConfigTypes.VALUE);
    if (valueNode != null) {
      return valueNode.getText();
    } else {
      return null;
    }
  }
}