// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class PythonFoldingBuilder extends CustomFoldingBuilder implements DumbAware {

  public static final TokenSet FOLDABLE_COLLECTIONS_LITERALS = TokenSet.create(
                                                     PyElementTypes.SET_LITERAL_EXPRESSION,
                                                     PyElementTypes.DICT_LITERAL_EXPRESSION,
                                                     PyElementTypes.GENERATOR_EXPRESSION,
                                                     PyElementTypes.SET_COMP_EXPRESSION,
                                                     PyElementTypes.DICT_COMP_EXPRESSION,
                                                     PyElementTypes.LIST_LITERAL_EXPRESSION,
                                                     PyElementTypes.LIST_COMP_EXPRESSION,
                                                     PyElementTypes.TUPLE_EXPRESSION);

  public static final String PYTHON_TYPE_ANNOTATION_GROUP_NAME = "Python type annotation";

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                          @NotNull PsiElement root,
                                          @NotNull Document document,
                                          boolean quick) {
    appendDescriptors(root.getNode(), descriptors);
  }

  private static void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
    IElementType elementType = node.getElementType();
    if (node.getPsi() instanceof PyFile) {
      final List<PyImportStatementBase> imports = ((PyFile)node.getPsi()).getImportBlock();
      if (imports.size() > 1) {
        final PyImportStatementBase firstImport = imports.get(0);
        final PyImportStatementBase lastImport = imports.get(imports.size()-1);
        descriptors.add(new FoldingDescriptor(firstImport, new TextRange(firstImport.getTextRange().getStartOffset(),
                                                                         lastImport.getTextRange().getEndOffset())));
      }
    }
    else if (elementType == PyElementTypes.MATCH_STATEMENT) {
      foldMatchStatement(node, descriptors);
    }
    else if (elementType == PyElementTypes.STATEMENT_LIST) {
      foldStatementList(node, descriptors);
    }
    else if (elementType == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      foldLongStrings(node, descriptors);
    }
    else if (FOLDABLE_COLLECTIONS_LITERALS.contains(elementType)) {
      foldCollectionLiteral(node, descriptors);
    }
    else if (elementType == PyTokenTypes.END_OF_LINE_COMMENT) {
      foldSequentialComments(node, descriptors);
    }
    else if (elementType == PyElementTypes.ANNOTATION) {
      var annotation = node.getPsi();
      if (annotation instanceof PyAnnotation pyAnnotation && pyAnnotation.getValue() != null) {
        descriptors.add(new FoldingDescriptor(node, pyAnnotation.getValue().getTextRange(),
                                              FoldingGroup.newGroup(PYTHON_TYPE_ANNOTATION_GROUP_NAME)));
      }
    }
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, descriptors);
      child = child.getTreeNext();
    }
  }

  private static void foldSequentialComments(ASTNode node, List<FoldingDescriptor> descriptors) {
    //do not start folded comments from custom region
    if (isCustomRegionElement(node.getPsi())) {
      return;
    }
    //need to skip previous comments in sequence
    ASTNode curNode = node.getTreePrev();
    while (curNode != null) {
      if (curNode.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
        if (isCustomRegionElement(curNode.getPsi())) {
          break;
        }
        return;
      }
      curNode = curNode.getPsi() instanceof PsiWhiteSpace ? curNode.getTreePrev() : null;
    }

    //fold sequence comments in one block
    curNode = node.getTreeNext();
    ASTNode lastCommentNode = node;
    while (curNode != null) {
      if (curNode.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
        //do not end folded comments with custom region
        if (isCustomRegionElement(curNode.getPsi())) {
          break;
        }
        lastCommentNode = curNode;
        curNode = curNode.getTreeNext();
        continue;
      }
      curNode = curNode.getPsi() instanceof PsiWhiteSpace ? curNode.getTreeNext() : null;
    }

    if (lastCommentNode != node) {
      descriptors.add(new FoldingDescriptor(node, TextRange.create(node.getStartOffset(), lastCommentNode.getTextRange().getEndOffset())));
    }

  }

  private static void foldCollectionLiteral(ASTNode node, List<FoldingDescriptor> descriptors) {
    if (StringUtil.countNewLines(node.getChars()) > 0) {
      TextRange range = node.getTextRange();
      int delta = node.getElementType() == PyElementTypes.TUPLE_EXPRESSION ? 0 : 1;
      descriptors.add(new FoldingDescriptor(node, TextRange.create(range.getStartOffset() + delta, range.getEndOffset() - delta)));
    }
  }

  private static void foldMatchStatement(@NotNull ASTNode node, @NotNull List<FoldingDescriptor> descriptors) {
    TextRange nodeRange = node.getTextRange();
    if (nodeRange.isEmpty()) {
      return;
    }

    IElementType elType = node.getElementType();
    if (elType == PyElementTypes.MATCH_STATEMENT) {
      ASTNode colon = node.findChildByType(PyTokenTypes.COLON);
      foldSegment(node, descriptors, nodeRange, colon);
    }
  }

  private static void foldSegment(@NotNull ASTNode node, @NotNull List<FoldingDescriptor> descriptors, @NotNull TextRange nodeRange, @Nullable ASTNode colon) {
    int nodeEnd = nodeRange.getEndOffset();
    if (colon != null && nodeEnd - (colon.getStartOffset() + 1) > 1) {
      CharSequence chars = node.getChars();
      int nodeStart = nodeRange.getStartOffset();
      int foldStart = colon.getStartOffset() + 1;
      int foldEnd = nodeEnd;
      while (foldEnd > Math.max(nodeStart, foldStart + 1) && Character.isWhitespace(chars.charAt(foldEnd - nodeStart - 1))) {
        foldEnd--;
      }
      descriptors.add(new FoldingDescriptor(node, new TextRange(foldStart, foldEnd)));
    }
    else if (nodeRange.getLength() > 1) { // only for ranges at least 1 char wide
      descriptors.add(new FoldingDescriptor(node, nodeRange));
    }
  }

  private static void foldStatementList(ASTNode node, List<FoldingDescriptor> descriptors) {
    final TextRange nodeRange = node.getTextRange();
    if (nodeRange.isEmpty()) {
      return;
    }

    final IElementType elType = node.getTreeParent().getElementType();
    if (elType == PyElementTypes.FUNCTION_DECLARATION || elType == PyElementTypes.CLASS_DECLARATION || checkFoldBlocks(node, elType)) {
      ASTNode colon = node.getTreeParent().findChildByType(PyTokenTypes.COLON);
      foldSegment(node, descriptors, nodeRange, colon);
    }
  }

  private static boolean checkFoldBlocks(@NotNull ASTNode statementList, @NotNull IElementType parentType) {
    PsiElement element = statementList.getPsi();
    assert element instanceof PyStatementList;

    return PyElementTypes.PARTS.contains(parentType) ||
           parentType == PyElementTypes.WITH_STATEMENT ||
           parentType == PyElementTypes.CASE_CLAUSE;
  }

  private static void foldLongStrings(ASTNode node, List<FoldingDescriptor> descriptors) {
    //don't want to fold docstrings like """\n string \n """
    boolean shouldFoldDocString = getDocStringOwnerType(node) != null && StringUtil.countNewLines(node.getChars()) > 1;
    boolean shouldFoldString = getDocStringOwnerType(node) == null && StringUtil.countNewLines(node.getChars()) > 0;
    if (shouldFoldDocString || shouldFoldString) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
  }

  private static @Nullable IElementType getDocStringOwnerType(ASTNode node) {
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
    if (isImport(node)) {
      return "import ...";
    }
    if (node.getElementType() == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      PyStringLiteralExpression stringLiteralExpression = (PyStringLiteralExpression)node.getPsi();
      String prefix = stringLiteralExpression.getStringElements().get(0).getPrefix();
      if (stringLiteralExpression.isDocString()) {
        final String stringValue = stringLiteralExpression.getStringValue().trim();
        final String[] lines = LineTokenizer.tokenize(stringValue, true);
        if (lines.length > 2 && lines[1].trim().length() == 0) {
          return prefix + "\"\"\"" + lines[0].trim() + "...\"\"\"";
        }
        return prefix + "\"\"\"...\"\"\"";
      } else {
        return prefix + getLanguagePlaceholderForString(stringLiteralExpression);
      }
    }
    return "...";
  }

  private static String getLanguagePlaceholderForString(PyStringLiteralExpression stringLiteralExpression) {
    String stringText = stringLiteralExpression.getText();
    Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(stringText);
    if (quotes != null) {
      return quotes.second + "..." + quotes.second;
    }
    return "...";
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
    if (isImport(node)) {
      return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
    }
    var elementType = node.getElementType();
    if (elementType == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      if (getDocStringOwnerType(node) == PyElementTypes.FUNCTION_DECLARATION && CodeFoldingSettings.getInstance().COLLAPSE_METHODS) {
        // method will be collapsed, no need to also collapse docstring
        return false;
      }
      if (getDocStringOwnerType(node) != null) {
        return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
      }
      return PythonFoldingSettings.getInstance().isCollapseLongStrings();
    }
    if (elementType == PyTokenTypes.END_OF_LINE_COMMENT) {
      return PythonFoldingSettings.getInstance().isCollapseSequentialComments();
    }
    if (elementType == PyElementTypes.ANNOTATION) {
      return PythonFoldingSettings.getInstance().isCollapseTypeAnnotations();
    }
    if (elementType == PyElementTypes.STATEMENT_LIST && node.getTreeParent().getElementType() == PyElementTypes.FUNCTION_DECLARATION) {
      return CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
    }
    if (FOLDABLE_COLLECTIONS_LITERALS.contains(elementType)) {
      return PythonFoldingSettings.getInstance().isCollapseLongCollections();
    }
    return false;
  }

  @Override
  protected boolean isCustomFoldingCandidate(@NotNull ASTNode node) {
    return node.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT;
  }

  @Override
  protected boolean isCustomFoldingRoot(@NotNull ASTNode node) {
    return node.getPsi() instanceof PyFile || node.getElementType() == PyElementTypes.STATEMENT_LIST;
  }

  private static boolean isImport(@NotNull ASTNode node) {
    return PyElementTypes.IMPORT_STATEMENTS.contains(node.getElementType());
  }

}
