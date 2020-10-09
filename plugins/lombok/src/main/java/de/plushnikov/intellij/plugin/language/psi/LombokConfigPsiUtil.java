package de.plushnikov.intellij.plugin.language.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class LombokConfigPsiUtil {
  public static String getKey(@NotNull LombokConfigCleaner element) {
    ASTNode keyNode = element.getNode().findChildByType(LombokConfigTypes.KEY);
    if (keyNode != null) {
      return keyNode.getText();
    } else {
      return null;
    }
  }

  public static String getKey(@NotNull LombokConfigProperty element) {
    ASTNode keyNode = element.getNode().findChildByType(LombokConfigTypes.KEY);
    if (keyNode != null) {
      return keyNode.getText();
    } else {
      return null;
    }
  }

  public static String getValue(@NotNull LombokConfigProperty element) {
    ASTNode valueNode = element.getNode().findChildByType(LombokConfigTypes.VALUE);
    if (valueNode != null) {
      return valueNode.getText();
    } else {
      return null;
    }
  }

  public static String getSign(@NotNull LombokConfigProperty element) {
    ASTNode valueNode = element.getOperation().getNode().findChildByType(LombokConfigTypes.SIGN);
    if (valueNode != null) {
      return StringUtil.trim(valueNode.getText());
    } else {
      return null;
    }
  }
}