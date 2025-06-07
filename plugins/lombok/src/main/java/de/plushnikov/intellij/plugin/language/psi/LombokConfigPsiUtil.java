package de.plushnikov.intellij.plugin.language.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LombokConfigPsiUtil {
  public static String getKey(@NotNull LombokConfigCleaner element) {
    return getNodeText(element.getNode(), LombokConfigTypes.KEY);
  }

  public static String getKey(@NotNull LombokConfigProperty element) {
    return getNodeText(element.getNode(), LombokConfigTypes.KEY);
  }

  public static String getValue(@NotNull LombokConfigProperty element) {
    return getNodeText(element.getNode(), LombokConfigTypes.VALUE);
  }

  public static String getSign(@NotNull LombokConfigProperty element) {
    return StringUtil.trim(getNodeText(element.getOperation().getNode(), LombokConfigTypes.SIGN));
  }

  public static String getSign(@NotNull LombokConfigOperation element) {
    return StringUtil.trim(getNodeText(element.getNode(), LombokConfigTypes.SIGN));
  }

  private static @Nullable String getNodeText(@NotNull ASTNode node, @NotNull IElementType type) {
    final ASTNode valueNode = node.findChildByType(type);
    if (valueNode != null) {
      return valueNode.getText();
    } else {
      return null;
    }
  }
}
