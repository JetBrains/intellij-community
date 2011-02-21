package com.jetbrains.python;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonFoldingBuilder implements FoldingBuilder, DumbAware {
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private static void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
    if (node.getElementType() instanceof PyFileElementType) {
      ASTNode firstImport = node.getFirstChildNode();
      while(firstImport != null && !isImport(firstImport, false)) {
        firstImport = firstImport.getTreeNext();
      }
      if (firstImport != null) {
        ASTNode lastImport = firstImport.getTreeNext();
        while(lastImport != null && isImport(lastImport.getTreeNext(), true)) {
          lastImport = lastImport.getTreeNext();
        }
        if (lastImport != null) {
          while (lastImport.getElementType() == TokenType.WHITE_SPACE) {
            lastImport = lastImport.getTreePrev();
          }
          if (isImport(lastImport, false) && firstImport != lastImport) {
            descriptors.add(new FoldingDescriptor(firstImport, new TextRange(firstImport.getStartOffset(),
                                                                             lastImport.getTextRange().getEndOffset())));
          }
        }
      }
    }
    else if (node.getElementType() == PyElementTypes.STATEMENT_LIST) {
      IElementType elType = node.getTreeParent().getElementType();
      if (elType == PyElementTypes.FUNCTION_DECLARATION || elType == PyElementTypes.CLASS_DECLARATION) {
        ASTNode colon = node.getTreeParent().findChildByType(PyTokenTypes.COLON);
        if (colon != null && colon.getStartOffset() + 1 < node.getTextRange().getEndOffset() - 1) {
          final CharSequence chars = node.getChars();
          int nodeStart = node.getTextRange().getStartOffset();
          int endOffset = node.getTextRange().getEndOffset();
          while(endOffset > colon.getStartOffset()+2 && endOffset > nodeStart && Character.isWhitespace(chars.charAt(endOffset - nodeStart - 1))) {
            endOffset--;
          }
          descriptors.add(new FoldingDescriptor(node, new TextRange(colon.getStartOffset() + 1, endOffset)));
        }
        else {
          TextRange range = node.getTextRange();
          if (range.getStartOffset() < range.getEndOffset() - 1) { // only for ranges at least 1 char wide
            descriptors.add(new FoldingDescriptor(node, range));
          }
        }
      }
    }

    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, descriptors);
      child = child.getTreeNext();
    }
  }

  private static boolean isImport(ASTNode node, boolean orWhitespace) {
    if (node == null) return false;
    IElementType elementType = node.getElementType();
    if (orWhitespace && elementType == TokenType.WHITE_SPACE) {
      return true;
    }
    return elementType == PyElementTypes.IMPORT_STATEMENT || elementType == PyElementTypes.FROM_IMPORT_STATEMENT;
  }

  public String getPlaceholderText(@NotNull ASTNode node) {
    if (isImport(node, false)) {
      return "import ...";
    }
    return "...";
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    if (isImport(node, false)) {
      return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
    }
    return false;
  }
}
