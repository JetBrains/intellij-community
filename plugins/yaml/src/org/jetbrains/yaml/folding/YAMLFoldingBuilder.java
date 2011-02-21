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
  private static final TokenSet COMPOUND_VALUE = TokenSet.create(YAMLElementTypes.COMPOUND_VALUE);

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode astNode, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new LinkedList<FoldingDescriptor>();
    collectDescriptors(astNode, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private void collectDescriptors(@NotNull final ASTNode node, @NotNull final List<FoldingDescriptor> descriptors) {
    final IElementType type = node.getElementType();
    if (!StringUtil.isEmptyOrSpaces(node.getText())) {
      if (type == YAMLElementTypes.KEY_VALUE_PAIR) {
        final ASTNode[] children = node.getChildren(COMPOUND_VALUE);
        // We should ignore empty compound values
        if (children.length > 0 && !StringUtil.isEmpty(children[0].getText().trim())){
          descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
        }
      }
      if (type == YAMLElementTypes.DOCUMENT &&
          node.getTreeParent().getChildren(TokenSet.create(YAMLElementTypes.DOCUMENT)).length > 1){
        descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
      }
    }
    for (ASTNode child : node.getChildren(null)) {
      collectDescriptors(child, descriptors);
    }
  }

  @Nullable
  public String getPlaceholderText(@NotNull ASTNode node, TextRange range) {
    final IElementType type = node.getElementType();
    if (type == YAMLElementTypes.DOCUMENT){
      return "---";
    }
    if (type == YAMLElementTypes.KEY_VALUE_PAIR){
      return node.getFirstChildNode().getText();
    }
    return "...";
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}
