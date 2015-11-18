package org.jetbrains.yaml.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;

import java.util.LinkedList;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLFoldingBuilder implements FoldingBuilder, DumbAware {

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode astNode, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new LinkedList<FoldingDescriptor>();
    collectDescriptors(astNode, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private static void collectDescriptors(@NotNull final ASTNode node, @NotNull final List<FoldingDescriptor> descriptors) {
    final IElementType type = node.getElementType();
    final TextRange nodeTextRange = node.getTextRange();
    if (!StringUtil.isEmptyOrSpaces(node.getText()) && nodeTextRange.getLength() >= 2) {
      if (type == YAMLElementTypes.KEY_VALUE_PAIR) {
        final ASTNode valueNode = node.findChildByType(YAMLElementTypes.COMPOUND_VALUE);
        // We should ignore empty compound values
        if (valueNode != null && !StringUtil.isEmpty(valueNode.getText().trim())){
          descriptors.add(new FoldingDescriptor(node, nodeTextRange));
        }
      }
      if (type == YAMLElementTypes.DOCUMENT &&
          node.getTreeParent().getChildren(TokenSet.create(YAMLElementTypes.DOCUMENT)).length > 1){
        descriptors.add(new FoldingDescriptor(node, nodeTextRange));
      }
      if (type == YAMLElementTypes.SCALAR_TEXT_VALUE
          || type == YAMLElementTypes.SCALAR_LIST_VALUE
          || type == YAMLElementTypes.SCALAR_PLAIN_VALUE) {
        descriptors.add(new FoldingDescriptor(node, nodeTextRange));
      }
    }
    for (ASTNode child : node.getChildren(null)) {
      collectDescriptors(child, descriptors);
    }
  }

  @Nullable
  public String getPlaceholderText(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == YAMLElementTypes.DOCUMENT){
      return "---";
    }
    if (type == YAMLElementTypes.KEY_VALUE_PAIR){
      return node.getFirstChildNode().getText();
    }
    if (type == YAMLElementTypes.SCALAR_TEXT_VALUE || type == YAMLElementTypes.SCALAR_LIST_VALUE) {
      return node.getText().substring(0, 1);
    }
    return "...";
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}
