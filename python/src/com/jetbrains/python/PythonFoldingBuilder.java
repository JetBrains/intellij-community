/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFileElementType;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PythonFoldingBuilder extends CustomFoldingBuilder implements DumbAware {

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                          @NotNull PsiElement root,
                                          @NotNull Document document,
                                          boolean quick) {
    appendDescriptors(root.getNode(), descriptors);
  }

  private static void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
    if (node.getElementType() instanceof PyFileElementType) {
      final List<PyImportStatementBase> imports = ((PyFile)node.getPsi()).getImportBlock();
      if (imports.size() > 1) {
        final PyImportStatementBase firstImport = imports.get(0);
        final PyImportStatementBase lastImport = imports.get(imports.size()-1);
        descriptors.add(new FoldingDescriptor(firstImport, new TextRange(firstImport.getTextRange().getStartOffset(),
                                                                         lastImport.getTextRange().getEndOffset())));
      }
    }
    else if (node.getElementType() == PyElementTypes.STATEMENT_LIST) {
      foldStatementList(node, descriptors);
    }
    else if (node.getElementType() == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      foldDocString(node, descriptors);
    }

    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, descriptors);
      child = child.getTreeNext();
    }
  }

  private static void foldStatementList(ASTNode node, List<FoldingDescriptor> descriptors) {
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

  private static void foldDocString(ASTNode node, List<FoldingDescriptor> descriptors) {
    if (getDocStringOwnerType(node) != null && StringUtil.countChars(node.getText(), '\n') > 1) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
  }

  @Nullable
  private static IElementType getDocStringOwnerType(ASTNode node) {
    final ASTNode treeParent = node.getTreeParent();
    IElementType parentType = treeParent.getElementType();
    if (parentType == PyElementTypes.EXPRESSION_STATEMENT && treeParent.getTreeParent() != null) {
      final ASTNode parent2 = treeParent.getTreeParent();
      if (parent2.getElementType() == PyElementTypes.STATEMENT_LIST && parent2.getTreeParent() != null && treeParent == parent2.getFirstChildNode()) {
        final ASTNode parent3 = parent2.getTreeParent();
        if (parent3.getElementType() == PyElementTypes.FUNCTION_DECLARATION || parent3.getElementType() == PyElementTypes.CLASS_DECLARATION) {
          return parent3.getElementType();
        }
      }
      else if (parent2.getElementType() instanceof PyFileElementType) {
        return parent2.getElementType();
      }
    }
    return null;
  }

  @Override
  protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    if (PyFileImpl.isImport(node, false)) {
      return "import ...";
    }
    if (node.getElementType() == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      final String stringValue = ((PyStringLiteralExpression)node.getPsi()).getStringValue().trim();
      final String[] lines = LineTokenizer.tokenize(stringValue, true);
      if (lines.length > 2 && lines[1].trim().length() == 0) {
        return "\"\"\"" + lines [0].trim() + "...\"\"\"";
      }
      return "\"\"\"...\"\"\"";
    }
    return "...";
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
    if (PyFileImpl.isImport(node, false)) {
      return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
    }
    if (node.getElementType() == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      if (getDocStringOwnerType(node) == PyElementTypes.FUNCTION_DECLARATION && CodeFoldingSettings.getInstance().COLLAPSE_METHODS) {
        // method will be collapsed, no need to also collapse docstring
        return false;
      }
      return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
    }
    if (node.getElementType() == PyElementTypes.STATEMENT_LIST && node.getTreeParent().getElementType() == PyElementTypes.FUNCTION_DECLARATION) {
      return CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
    }
    return false;
  }

  @Override
  protected boolean isCustomFoldingCandidate(ASTNode node) {
    return node.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT;
  }

  @Override
  protected boolean isCustomFoldingRoot(ASTNode node) {
    return node.getPsi() instanceof PyFile || node.getElementType() == PyElementTypes.STATEMENT_LIST;
  }
}
