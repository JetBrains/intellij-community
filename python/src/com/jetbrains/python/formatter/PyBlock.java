/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.formatter.PyCodeStyleSettings.DICT_ALIGNMENT_ON_COLON;
import static com.jetbrains.python.formatter.PyCodeStyleSettings.DICT_ALIGNMENT_ON_VALUE;
import static com.jetbrains.python.formatter.PythonFormattingModelBuilder.STATEMENT_OR_DECLARATION;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyBlock implements ASTBlock {
  private static final TokenSet ourListElementTypes = TokenSet.create(PyElementTypes.LIST_LITERAL_EXPRESSION,
                                                                      PyElementTypes.LIST_COMP_EXPRESSION,
                                                                      PyElementTypes.DICT_LITERAL_EXPRESSION,
                                                                      PyElementTypes.DICT_COMP_EXPRESSION,
                                                                      PyElementTypes.SET_LITERAL_EXPRESSION,
                                                                      PyElementTypes.SET_COMP_EXPRESSION,
                                                                      PyElementTypes.ARGUMENT_LIST,
                                                                      PyElementTypes.PARAMETER_LIST,
                                                                      PyElementTypes.TUPLE_EXPRESSION,
                                                                      PyElementTypes.PARENTHESIZED_EXPRESSION,
                                                                      PyElementTypes.SLICE_EXPRESSION,
                                                                      PyElementTypes.SUBSCRIPTION_EXPRESSION,
                                                                      PyElementTypes.GENERATOR_EXPRESSION);

  private static final TokenSet ourBrackets = TokenSet.create(PyTokenTypes.LPAR, PyTokenTypes.RPAR,
                                                              PyTokenTypes.LBRACE, PyTokenTypes.RBRACE,
                                                              PyTokenTypes.LBRACKET, PyTokenTypes.RBRACKET);

  private static final TokenSet ourHangingIndentOwners = TokenSet.create(PyElementTypes.LIST_LITERAL_EXPRESSION,
                                                                         PyElementTypes.LIST_COMP_EXPRESSION,
                                                                         PyElementTypes.DICT_LITERAL_EXPRESSION,
                                                                         PyElementTypes.DICT_COMP_EXPRESSION,
                                                                         PyElementTypes.SET_LITERAL_EXPRESSION,
                                                                         PyElementTypes.SET_COMP_EXPRESSION,
                                                                         PyElementTypes.ARGUMENT_LIST,
                                                                         PyElementTypes.PARAMETER_LIST,
                                                                         PyElementTypes.TUPLE_EXPRESSION,
                                                                         PyElementTypes.PARENTHESIZED_EXPRESSION,
                                                                         PyElementTypes.GENERATOR_EXPRESSION,
                                                                         PyElementTypes.FUNCTION_DECLARATION,
                                                                         PyElementTypes.CALL_EXPRESSION,
                                                                         PyElementTypes.FROM_IMPORT_STATEMENT);

  public static final Key<Boolean> IMPORT_GROUP_BEGIN = Key.create("com.jetbrains.python.formatter.importGroupBegin");

  private final PyBlock myParent;
  private final Alignment myAlignment;
  private final Indent myIndent;
  private final ASTNode myNode;
  private final Wrap myWrap;
  private final PyBlockContext myContext;
  private List<PyBlock> mySubBlocks = null;
  private Map<ASTNode, PyBlock> mySubBlockByNode = null;
  private final boolean myEmptySequence;

  // Shared among multiple children sub-blocks
  private Alignment myChildAlignment = null;
  private Alignment myDictAlignment = null;
  private Wrap myDictWrapping = null;
  private Wrap myFromImportWrapping = null;

  public PyBlock(@Nullable PyBlock parent,
                 @NotNull ASTNode node,
                 @Nullable Alignment alignment,
                 @NotNull Indent indent,
                 @Nullable Wrap wrap,
                 @NotNull PyBlockContext context) {
    myParent = parent;
    myAlignment = alignment;
    myIndent = indent;
    myNode = node;
    myWrap = wrap;
    myContext = context;
    myEmptySequence = isEmptySequence(node);

    final PyCodeStyleSettings pySettings = myContext.getPySettings();
    if (node.getElementType() == PyElementTypes.DICT_LITERAL_EXPRESSION) {
      myDictAlignment = Alignment.createAlignment(true);
      myDictWrapping = Wrap.createWrap(pySettings.DICT_WRAPPING, true);
    }
    else if (node.getElementType() == PyElementTypes.FROM_IMPORT_STATEMENT) {
      myFromImportWrapping = Wrap.createWrap(pySettings.FROM_IMPORT_WRAPPING, false);
    }
  }

  @Override
  @NotNull
  public ASTNode getNode() {
    return myNode;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  private Alignment getAlignmentForChildren() {
    if (myChildAlignment == null) {
      myChildAlignment = Alignment.createAlignment();
    }
    return myChildAlignment;
  }

  @Override
  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlockByNode = buildSubBlocks();
      mySubBlocks = new ArrayList<>(mySubBlockByNode.values());
    }
    return Collections.unmodifiableList(mySubBlocks);
  }

  @Nullable
  private PyBlock getSubBlockByNode(@NotNull ASTNode node) {
    return mySubBlockByNode.get(node);
  }

  @Nullable
  private PyBlock getSubBlockByIndex(int index) {
    return mySubBlocks.get(index);
  }

  @NotNull
  private Map<ASTNode, PyBlock> buildSubBlocks() {
    final Map<ASTNode, PyBlock> blocks = new LinkedHashMap<>();
    for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {

      final IElementType childType = child.getElementType();

      if (child.getTextRange().isEmpty()) continue;

      if (childType == TokenType.WHITE_SPACE) {
        continue;
      }

      blocks.put(child, buildSubBlock(child));
    }
    return Collections.unmodifiableMap(blocks);
  }

  @NotNull
  private PyBlock buildSubBlock(@NotNull ASTNode child) {
    final IElementType parentType = myNode.getElementType();

    final ASTNode grandParentNode = myNode.getTreeParent();
    final IElementType grandparentType = grandParentNode == null ? null : grandParentNode.getElementType();

    final IElementType childType = child.getElementType();
    Wrap childWrap = null;
    Indent childIndent = Indent.getNoneIndent();
    Alignment childAlignment = null;

    final PyCodeStyleSettings settings = myContext.getPySettings();

    if (parentType == PyElementTypes.BINARY_EXPRESSION && !isInControlStatement()) {
      //Setup alignments for binary expression
      childAlignment = getAlignmentForChildren();

      PyBlock p = myParent; //Check grandparents
      while (p != null) {
        final ASTNode pNode = p.getNode();
        if (ourListElementTypes.contains(pNode.getElementType())) {
          if (needListAlignment(child) && !myEmptySequence) {

            childAlignment = p.getChildAlignment();
            break;
          }
        }
        else if (pNode == PyElementTypes.BINARY_EXPRESSION) {
          childAlignment = p.getChildAlignment();
        }
        if (!breaksAlignment(pNode.getElementType())) {
          p = p.myParent;
        }
        else {
          break;
        }
      }
    }

    if (childType == PyElementTypes.STATEMENT_LIST) {
      if (hasLineBreaksBeforeInSameParent(child, 1) || needLineBreakInStatement()) {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (childType == PyElementTypes.IMPORT_ELEMENT) {
      if (parentType == PyElementTypes.FROM_IMPORT_STATEMENT) {
        childWrap = myFromImportWrapping;
      }
      else {
        childWrap = Wrap.createWrap(WrapType.NORMAL, true);
      }
      childIndent = Indent.getNormalIndent();
    }
    if (childType == PyTokenTypes.END_OF_LINE_COMMENT && parentType == PyElementTypes.FROM_IMPORT_STATEMENT) {
      childIndent = Indent.getNormalIndent();
    }
    if (ourListElementTypes.contains(parentType)) {
      // wrapping in non-parenthesized tuple expression is not allowed (PY-1792)
      if ((parentType != PyElementTypes.TUPLE_EXPRESSION || grandparentType == PyElementTypes.PARENTHESIZED_EXPRESSION) &&
          !ourBrackets.contains(childType) &&
          childType != PyTokenTypes.COMMA &&
          !isSliceOperand(child) /*&& !isSubscriptionOperand(child)*/) {
        childWrap = Wrap.createWrap(WrapType.NORMAL, true);
      }
      if (needListAlignment(child) && !myEmptySequence) {
        childAlignment = getAlignmentForChildren();
      }
      if (childType == PyTokenTypes.END_OF_LINE_COMMENT) {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.BINARY_EXPRESSION &&
             (PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens().contains(childType) ||
              PyTokenTypes.OPERATIONS.contains(childType))) {
      if (isInControlStatement()) {
        final PyParenthesizedExpression parens = PsiTreeUtil.getParentOfType(myNode.getPsi(), PyParenthesizedExpression.class, true,
                                                                       PyStatementPart.class);
        childIndent = parens != null ? Indent.getNormalIndent() : Indent.getContinuationIndent();
      }
    }


    if (parentType == PyElementTypes.LIST_LITERAL_EXPRESSION || parentType == PyElementTypes.LIST_COMP_EXPRESSION) {
      if ((childType == PyTokenTypes.RBRACKET && !settings.HANG_CLOSING_BRACKETS) || childType == PyTokenTypes.LBRACKET) {
        childIndent = Indent.getNoneIndent();
      }
      else {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.DICT_LITERAL_EXPRESSION || parentType == PyElementTypes.SET_LITERAL_EXPRESSION ||
             parentType == PyElementTypes.SET_COMP_EXPRESSION || parentType == PyElementTypes.DICT_COMP_EXPRESSION) {
      if ((childType == PyTokenTypes.RBRACE && !settings.HANG_CLOSING_BRACKETS) || !hasLineBreaksBeforeInSameParent(child, 1)) {
        childIndent = Indent.getNoneIndent();
      }
      else {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      if (PyTokenTypes.STRING_NODES.contains(childType)) {
        childAlignment = getAlignmentForChildren();
      }
    }
    else if (parentType == PyElementTypes.FROM_IMPORT_STATEMENT) {
      if (myNode.findChildByType(PyTokenTypes.LPAR) != null) {
        if (childType == PyElementTypes.IMPORT_ELEMENT) {
          if (settings.ALIGN_MULTILINE_IMPORTS) {
            childAlignment = getAlignmentForChildren();
          }
          else {
            childIndent = Indent.getNormalIndent();
          }
        }
        if (childType == PyTokenTypes.RPAR) {
          childIndent = Indent.getNoneIndent();
          // Don't have hanging indent and is not going to have it due to the setting about opening parenthesis
          if (!hasHangingIndent(myNode.getPsi()) && !settings.FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS) {
            childAlignment = getAlignmentForChildren();
          }
          else if (settings.HANG_CLOSING_BRACKETS) {
            childIndent = Indent.getNormalIndent();
          }
        }
      }
    }
    else if (isValueOfKeyValuePair(child)) {
      childIndent = Indent.getNormalIndent();
    }
    //Align elements vertically if there is an argument in the first line of parenthesized expression
    else if (!hasHangingIndent(myNode.getPsi()) &&
             ((parentType == PyElementTypes.PARENTHESIZED_EXPRESSION && myContext.getSettings().ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION) ||
              (parentType == PyElementTypes.ARGUMENT_LIST && myContext.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS) ||
              (parentType == PyElementTypes.PARAMETER_LIST && myContext.getSettings().ALIGN_MULTILINE_PARAMETERS)) &&
             !isIndentNext(child) &&
             !hasLineBreaksBeforeInSameParent(myNode.getFirstChildNode(), 1) &&
             !ourListElementTypes.contains(childType)) {

      if (!ourBrackets.contains(childType)) {
        childAlignment = getAlignmentForChildren();
        if (parentType != PyElementTypes.CALL_EXPRESSION) {
          childIndent = Indent.getNormalIndent();
        }
      }
      else if (childType == PyTokenTypes.RPAR) {
        childIndent = Indent.getNoneIndent();
      }
    }
    else if (parentType == PyElementTypes.GENERATOR_EXPRESSION || parentType == PyElementTypes.PARENTHESIZED_EXPRESSION) {
      final boolean tupleOrGenerator = parentType == PyElementTypes.GENERATOR_EXPRESSION ||
                                       myNode.getPsi(PyParenthesizedExpression.class).getContainedExpression() instanceof PyTupleExpression;
      if ((childType == PyTokenTypes.RPAR && !(tupleOrGenerator && settings.HANG_CLOSING_BRACKETS)) ||
          !hasLineBreaksBeforeInSameParent(child, 1)) {
        childIndent = Indent.getNoneIndent();
      }
      else {
        childIndent = isIndentNext(child) ? Indent.getContinuationIndent() : Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.ARGUMENT_LIST || parentType == PyElementTypes.PARAMETER_LIST) {
      if (childType == PyTokenTypes.RPAR && !settings.HANG_CLOSING_BRACKETS) {
        childIndent = Indent.getNoneIndent();
      }
      else if (parentType == PyElementTypes.PARAMETER_LIST ||
               settings.USE_CONTINUATION_INDENT_FOR_ARGUMENTS ||
               argumentMayHaveSameIndentAsFollowingStatementList()) {
        childIndent = Indent.getContinuationIndent();
      }
      else {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.SUBSCRIPTION_EXPRESSION) {
      final PyExpression indexExpression = ((PySubscriptionExpression)myNode.getPsi()).getIndexExpression();
      if (indexExpression != null && child == indexExpression.getNode()) {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.REFERENCE_EXPRESSION) {
      if (child != myNode.getFirstChildNode()) {
        childIndent = Indent.getNormalIndent();
        if (hasLineBreaksBeforeInSameParent(child, 1)) {
          if (isInControlStatement()) {
            childIndent = Indent.getContinuationIndent();
          }
          else {
            PyBlock b = myParent;
            while (b != null) {
              if (b.getNode().getPsi() instanceof PyParenthesizedExpression ||
                  b.getNode().getPsi() instanceof PyArgumentList ||
                  b.getNode().getPsi() instanceof PyParameterList) {
                childAlignment = getAlignmentOfChild(b, 1);
                break;
              }
              b = b.myParent;
            }
          }
        }
      }
    }
    if (childType == PyElementTypes.KEY_VALUE_EXPRESSION && isChildOfDictLiteral(child)) {
      childWrap = myDictWrapping;
      childIndent = Indent.getNormalIndent();
    }

    if (isAfterStatementList(child) && !hasLineBreaksBeforeInSameParent(child, 2) && child.getElementType() != PyTokenTypes.END_OF_LINE_COMMENT) {
      // maybe enter was pressed and cut us from a previous (nested) statement list
      childIndent = Indent.getNormalIndent();
    }

    if (settings.DICT_ALIGNMENT == DICT_ALIGNMENT_ON_VALUE) {
      if (isValueOfKeyValuePairOfDictLiteral(child) && !ourListElementTypes.contains(childType)) {
        childAlignment = myParent.myDictAlignment;
      }
      else if (isValueOfKeyValuePairOfDictLiteral(myNode) &&
               ourListElementTypes.contains(parentType) &&
               PyTokenTypes.OPEN_BRACES.contains(childType)) {
        childAlignment = myParent.myParent.myDictAlignment;
      }
    }
    else if (myContext.getPySettings().DICT_ALIGNMENT == DICT_ALIGNMENT_ON_COLON) {
      if (isChildOfKeyValuePairOfDictLiteral(child) && childType == PyTokenTypes.COLON) {
        childAlignment = myParent.myDictAlignment;
      }
    }

    ASTNode prev = child.getTreePrev();
    while (prev != null && prev.getElementType() == TokenType.WHITE_SPACE) {
      if (prev.textContains('\\') &&
          !childIndent.equals(Indent.getContinuationIndent(false)) &&
          !childIndent.equals(Indent.getContinuationIndent(true))) {
        childIndent = isIndentNext(child) ? Indent.getContinuationIndent() : Indent.getNormalIndent();
        break;
      }
      prev = prev.getTreePrev();
    }

    return new PyBlock(this, child, childAlignment, childIndent, childWrap, myContext);
  }

  private static boolean isValueOfKeyValuePairOfDictLiteral(@NotNull ASTNode node) {
    return isValueOfKeyValuePair(node) && isChildOfDictLiteral(node.getTreeParent());
  }

  private static boolean isChildOfKeyValuePairOfDictLiteral(@NotNull ASTNode node) {
    return isChildOfKeyValuePair(node) && isChildOfDictLiteral(node.getTreeParent());
  }

  private static boolean isChildOfDictLiteral(@NotNull ASTNode node) {
    final ASTNode nodeParent = node.getTreeParent();
    return nodeParent != null && nodeParent.getElementType() == PyElementTypes.DICT_LITERAL_EXPRESSION;
  }

  private static boolean isChildOfKeyValuePair(@NotNull ASTNode node) {
    final ASTNode nodeParent = node.getTreeParent();
    return nodeParent != null && nodeParent.getElementType() == PyElementTypes.KEY_VALUE_EXPRESSION;
  }

  private static boolean isValueOfKeyValuePair(@NotNull ASTNode node) {
    return isChildOfKeyValuePair(node) && node.getTreeParent().getPsi(PyKeyValueExpression.class).getValue() == node.getPsi();
  }

  private static boolean isEmptySequence(@NotNull ASTNode node) {
    return node.getPsi() instanceof PySequenceExpression && ((PySequenceExpression)node.getPsi()).isEmpty();
  }

  private boolean argumentMayHaveSameIndentAsFollowingStatementList() {
    if (myNode.getElementType() != PyElementTypes.ARGUMENT_LIST) {
      return false;
    }
    // This check is supposed to prevent PEP8's error: Continuation line with the same indent as next logical line
    final PsiElement header = getControlStatementHeader(myNode);
    if (header instanceof PyStatementListContainer) {
      final PyStatementList statementList = ((PyStatementListContainer)header).getStatementList();
      return PyUtil.onSameLine(header, myNode.getPsi()) && !PyUtil.onSameLine(header, statementList);
    }
    return false;
  }

  // Check https://www.python.org/dev/peps/pep-0008/#indentation
  private static boolean hasHangingIndent(@NotNull PsiElement elem) {
    if (elem instanceof PyCallExpression) {
      final PyArgumentList argumentList = ((PyCallExpression)elem).getArgumentList();
      return argumentList != null && hasHangingIndent(argumentList);
    }
    else if (elem instanceof PyFunction) {
      return hasHangingIndent(((PyFunction)elem).getParameterList());
    }

    final PsiElement firstChild;
    if (elem instanceof PyFromImportStatement) {
      firstChild = ((PyFromImportStatement)elem).getLeftParen();
    }
    else {
      firstChild = elem.getFirstChild();
    }
    if (firstChild == null) {
      return false;
    }
    final IElementType elementType = elem.getNode().getElementType();
    final ASTNode firstChildNode = firstChild.getNode();
    if (ourHangingIndentOwners.contains(elementType) && PyTokenTypes.OPEN_BRACES.contains(firstChildNode.getElementType())) {
      if (hasLineBreakAfterIgnoringComments(firstChildNode)) {
        return true;
      }
      final PsiElement firstItem = getFirstItem(elem);
      if (firstItem == null) {
        return !PyTokenTypes.CLOSE_BRACES.contains(elem.getLastChild().getNode().getElementType());
      }
      else {
        if (firstItem instanceof PyNamedParameter) {
          final PyExpression defaultValue = ((PyNamedParameter)firstItem).getDefaultValue();
          return defaultValue != null && hasHangingIndent(defaultValue);
        }
        else if (firstItem instanceof PyKeywordArgument) {
          final PyExpression valueExpression = ((PyKeywordArgument)firstItem).getValueExpression();
          return valueExpression != null && hasHangingIndent(valueExpression);
        }
        else if (firstItem instanceof PyKeyValueExpression) {
          final PyExpression value = ((PyKeyValueExpression)firstItem).getValue();
          return value != null && hasHangingIndent(value);
        }
        return hasHangingIndent(firstItem);
      }
    }
    else {
      return false;
    }
  }

  @Nullable
  private static PsiElement getFirstItem(@NotNull PsiElement elem) {
    PsiElement[] items = PsiElement.EMPTY_ARRAY;
    if (elem instanceof PySequenceExpression) {
      items = ((PySequenceExpression)elem).getElements();
    }
    else if (elem instanceof PyParameterList) {
      items = ((PyParameterList)elem).getParameters();
    }
    else if (elem instanceof PyArgumentList) {
      items = ((PyArgumentList)elem).getArguments();
    }
    else if (elem instanceof PyFromImportStatement) {
      items = ((PyFromImportStatement)elem).getImportElements();
    }
    else if (elem instanceof PyParenthesizedExpression) {
      final PyExpression containedExpression = ((PyParenthesizedExpression)elem).getContainedExpression();
      if (containedExpression instanceof PyTupleExpression) {
        items = ((PyTupleExpression)containedExpression).getElements();
      }
      else if (containedExpression != null) {
        return containedExpression;
      }
    }
    else if (elem instanceof PyComprehensionElement) {
      return ((PyComprehensionElement)elem).getResultExpression();
    }
    return ArrayUtil.getFirstElement(items);
  }

  private static boolean breaksAlignment(IElementType type) {
    return type != PyElementTypes.BINARY_EXPRESSION;
  }

  private static Alignment getAlignmentOfChild(@NotNull PyBlock b, int childNum) {
    if (b.getSubBlocks().size() > childNum) {
      final ChildAttributes attributes = b.getChildAttributes(childNum);
      return attributes.getAlignment();
    }
    return null;
  }

  private static boolean isIndentNext(@NotNull ASTNode child) {
    final PsiElement psi = PsiTreeUtil.getParentOfType(child.getPsi(), PyStatement.class);

    return psi instanceof PyIfStatement ||
           psi instanceof PyForStatement ||
           psi instanceof PyWithStatement ||
           psi instanceof PyClass ||
           psi instanceof PyFunction ||
           psi instanceof PyTryExceptStatement ||
           psi instanceof PyElsePart ||
           psi instanceof PyIfPart ||
           psi instanceof PyWhileStatement;
  }

  private static boolean isSubscriptionOperand(@NotNull ASTNode child) {
    return child.getTreeParent().getElementType() == PyElementTypes.SUBSCRIPTION_EXPRESSION &&
           child.getPsi() == ((PySubscriptionExpression)child.getTreeParent().getPsi()).getOperand();
  }

  private boolean isInControlStatement() {
    return getControlStatementHeader(myNode) != null;
  }

  @Nullable
  private static PsiElement getControlStatementHeader(@NotNull ASTNode node) {
    final PyStatementPart statementPart = PsiTreeUtil.getParentOfType(node.getPsi(), PyStatementPart.class, false, PyStatementList.class);
    if (statementPart != null) {
      return statementPart;
    }
    final PyWithItem withItem = PsiTreeUtil.getParentOfType(node.getPsi(), PyWithItem.class);
    if (withItem != null) {
      return withItem.getParent();
    }
    return null;
  }

  private boolean isSliceOperand(@NotNull ASTNode child) {
    if (myNode.getPsi() instanceof PySliceExpression) {
      final PySliceExpression sliceExpression = (PySliceExpression)myNode.getPsi();
      final PyExpression operand = sliceExpression.getOperand();
      return operand.getNode() == child;
    }
    return false;
  }

  private static boolean isAfterStatementList(@NotNull ASTNode child) {
    final PsiElement prev = child.getPsi().getPrevSibling();
    if (!(prev instanceof PyStatement)) {
      return false;
    }
    final PsiElement lastChild = PsiTreeUtil.getDeepestLast(prev);
    return lastChild.getParent() instanceof PyStatementList;
  }

  private boolean needListAlignment(@NotNull ASTNode child) {
    final IElementType childType = child.getElementType();
    if (PyTokenTypes.OPEN_BRACES.contains(childType)) {
      return false;
    }
    if (PyTokenTypes.CLOSE_BRACES.contains(childType)) {
      final ASTNode prevNonSpace = findPrevNonSpaceNode(child);
      if (prevNonSpace != null &&
          prevNonSpace.getElementType() == PyTokenTypes.COMMA &&
          myContext.getMode() == FormattingMode.ADJUST_INDENT) {
        return true;
      }
      return !hasHangingIndent(myNode.getPsi()) && !(myNode.getElementType() == PyElementTypes.DICT_LITERAL_EXPRESSION &&
                                                     myContext.getPySettings().DICT_NEW_LINE_AFTER_LEFT_BRACE);
    }
    if (myNode.getElementType() == PyElementTypes.ARGUMENT_LIST) {
      if (!myContext.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS || hasHangingIndent(myNode.getPsi())) {
        return false;
      }
      if (child.getElementType() == PyTokenTypes.COMMA) {
        return false;
      }
      return true;
    }
    if (myNode.getElementType() == PyElementTypes.PARAMETER_LIST) {
      return !hasHangingIndent(myNode.getPsi()) && myContext.getSettings().ALIGN_MULTILINE_PARAMETERS;
    }
    if (myNode.getElementType() == PyElementTypes.SUBSCRIPTION_EXPRESSION) {
      return false;
    }
    if (child.getElementType() == PyTokenTypes.COMMA) {
      return false;
    }
    return myContext.getPySettings().ALIGN_COLLECTIONS_AND_COMPREHENSIONS && !hasHangingIndent(myNode.getPsi());
  }

  @Nullable
  private static ASTNode findPrevNonSpaceNode(@NotNull ASTNode node) {
    do {
      node = node.getTreePrev();
    }
    while (isWhitespace(node));
    return node;
  }

  private static boolean isWhitespace(@Nullable ASTNode node) {
    return node != null && (node.getElementType() == TokenType.WHITE_SPACE || PyTokenTypes.WHITESPACE.contains(node.getElementType()));
  }

  private static boolean hasLineBreaksBeforeInSameParent(@NotNull ASTNode node, int minCount) {
    final ASTNode treePrev = node.getTreePrev();
    return (treePrev != null && isWhitespaceWithLineBreaks(TreeUtil.findLastLeaf(treePrev), minCount)) ||
           // Can happen, e.g. when you delete a statement from the beginning of a statement list
           isWhitespaceWithLineBreaks(node.getFirstChildNode(), minCount);
  }

  private static boolean hasLineBreakAfterIgnoringComments(@NotNull ASTNode node) {
    for (ASTNode next = TreeUtil.nextLeaf(node); next != null; next = TreeUtil.nextLeaf(next)) {
      if (isWhitespace(next)) {
        if (next.textContains('\n')) {
          return true;
        }
      }
      else if (next.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
        return true;
      }
      else {
        break;
      }
    }
    return false;
  }

  private static boolean isWhitespaceWithLineBreaks(@Nullable ASTNode node, int minCount) {
    if (isWhitespace(node)) {
      if (minCount == 1) {
        return node.textContains('\n');
      }
      final String prevNodeText = node.getText();
      int count = 0;
      for (int i = 0; i < prevNodeText.length(); i++) {
        if (prevNodeText.charAt(i) == '\n') {
          count++;
          if (count == minCount) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  @Nullable
  public Indent getIndent() {
    assert myIndent != null;
    return myIndent;
  }

  @Override
  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  @Nullable
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    final CommonCodeStyleSettings settings = myContext.getSettings();
    final PyCodeStyleSettings pySettings = myContext.getPySettings();
    if (child1 instanceof ASTBlock && child2 instanceof ASTBlock) {
      final ASTNode node1 = ((ASTBlock)child1).getNode();
      ASTNode node2 = ((ASTBlock)child2).getNode();
      final IElementType childType1 = node1.getElementType();
      final PsiElement psi1 = node1.getPsi();

      PsiElement psi2 = node2.getPsi();

      if (psi2 instanceof PyStatementList) {
        // Quite odd getSpacing() doesn't get called with child1=null for the first statement
        // in the statement list of a class, yet it does get called for the preceding colon and
        // the statement list itself. Hence we have to handle blank lines around methods here in
        // addition to SpacingBuilder.
        if (myNode.getElementType() == PyElementTypes.CLASS_DECLARATION) {
          final PyStatement[] statements = ((PyStatementList)psi2).getStatements();
          if (statements.length > 0 && statements[0] instanceof PyFunction) {
            return getBlankLinesForOption(settings.BLANK_LINES_AROUND_METHOD);
          }
        }
        if (childType1 == PyTokenTypes.COLON && needLineBreakInStatement()) {
          return Spacing.createSpacing(0, 0, 1, true, settings.KEEP_BLANK_LINES_IN_CODE);
        }
      }

      // pycodestyle.py enforces at most 2 blank lines only between comments directly
      // at the top-level of a file, not inside if, try/except, etc.
      if (psi1 instanceof PsiComment && myNode.getPsi() instanceof PsiFile) {
        return Spacing.createSpacing(0, 0, 1, true, 2);
      }

      // skip not inline comments to handles blank lines between various declarations
      if (psi2 instanceof PsiComment && hasLineBreaksBeforeInSameParent(node2, 1)) {
        final PsiElement nonCommentAfter = PyPsiUtils.getNextNonCommentSibling(psi2, true);
        if (nonCommentAfter != null) {
          psi2 = nonCommentAfter;
        }
      }
      node2 = psi2.getNode();
      final IElementType childType2 = psi2.getNode().getElementType();
      //noinspection ConstantConditions
      child2 = getSubBlockByNode(node2);

      if ((childType1 == PyTokenTypes.EQ || childType2 == PyTokenTypes.EQ)) {
        final PyNamedParameter namedParameter = as(myNode.getPsi(), PyNamedParameter.class);
        if (namedParameter != null && namedParameter.getAnnotation() != null) {
          return Spacing.createSpacing(1, 1, 0, settings.KEEP_LINE_BREAKS, settings.KEEP_BLANK_LINES_IN_CODE);
        }
      }

      if (psi1 instanceof PyImportStatementBase) {
        if (psi2 instanceof PyImportStatementBase) {
          final Boolean leftImportIsGroupStart = psi1.getCopyableUserData(IMPORT_GROUP_BEGIN);
          final Boolean rightImportIsGroupStart = psi2.getCopyableUserData(IMPORT_GROUP_BEGIN);
          // Cleanup user data, it's no longer needed
          psi1.putCopyableUserData(IMPORT_GROUP_BEGIN, null);
          // Don't remove IMPORT_GROUP_BEGIN from the element psi2 yet, because spacing is constructed pairwise:
          // it might be needed on the next iteration.
          //psi2.putCopyableUserData(IMPORT_GROUP_BEGIN, null);
          if (rightImportIsGroupStart != null) {
            return Spacing.createSpacing(0, 0, 2, true, 1);
          }
          else if (leftImportIsGroupStart != null) {
            // It's a trick to keep spacing consistent when new import statement is inserted
            // at the beginning of an import group, i.e. if there is a blank line before the next
            // import we want to save it, but remove line *after* inserted import.
            return Spacing.createSpacing(0, 0, 1, false, 0);
          }
        }
        if (psi2 instanceof PyStatement && !(psi2 instanceof PyImportStatementBase)) {
          if (PyUtil.isTopLevel(psi1)) {
            // If there is any function or class after a top-level import, it should be top-level as well
            if (PyElementTypes.CLASS_OR_FUNCTION.contains(childType2)) {
              return getBlankLinesForOption(Math.max(settings.BLANK_LINES_AFTER_IMPORTS,
                                                     pySettings.BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS));
            }
            return getBlankLinesForOption(settings.BLANK_LINES_AFTER_IMPORTS);
          }
          else {
            return getBlankLinesForOption(pySettings.BLANK_LINES_AFTER_LOCAL_IMPORTS);
          }
        }
      }

      if ((PyElementTypes.CLASS_OR_FUNCTION.contains(childType1) && STATEMENT_OR_DECLARATION.contains(childType2)) ||
          STATEMENT_OR_DECLARATION.contains(childType1) && PyElementTypes.CLASS_OR_FUNCTION.contains(childType2)) {
        if (PyUtil.isTopLevel(psi1)) {
          return getBlankLinesForOption(pySettings.BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS);
        }
      }

    }
    return myContext.getSpacingBuilder().getSpacing(this, child1, child2);
  }

  @NotNull
  private Spacing getBlankLinesForOption(int option) {
    final int blankLines = option + 1;
    return Spacing.createSpacing(0, 0, blankLines,
                                 myContext.getSettings().KEEP_LINE_BREAKS,
                                 myContext.getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS);
  }

  private boolean needLineBreakInStatement() {
    final PyStatement statement = PsiTreeUtil.getParentOfType(myNode.getPsi(), PyStatement.class);
    if (statement != null) {
      final Collection<PyStatementPart> parts = PsiTreeUtil.collectElementsOfType(statement, PyStatementPart.class);
      return (parts.size() == 1 && myContext.getPySettings().NEW_LINE_AFTER_COLON) ||
             (parts.size() > 1 && myContext.getPySettings().NEW_LINE_AFTER_COLON_MULTI_CLAUSE);
    }
    return false;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(int newChildIndex) {
    int statementListsBelow = 0;
    if (newChildIndex > 0) {
      // always pass decision to a sane block from top level from file or definition
      if (myNode.getPsi() instanceof PyFile || myNode.getElementType() == PyTokenTypes.COLON) {
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }

      final PyBlock insertAfterBlock = getSubBlockByIndex(newChildIndex - 1);

      final ASTNode prevNode = insertAfterBlock.getNode();
      final PsiElement prevElt = prevNode.getPsi();

      // stmt lists, parts and definitions should also think for themselves
      if (prevElt instanceof PyStatementList) {
        if (dedentAfterLastStatement((PyStatementList)prevElt)) {
          return new ChildAttributes(Indent.getNoneIndent(), getChildAlignment());
        }
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }
      else if (prevElt instanceof PyStatementPart) {
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }

      ASTNode lastChild = insertAfterBlock.getNode();

      // HACK? This code fragment is needed to make testClass2() pass,
      // but I don't quite understand why it is necessary and why the formatter
      // doesn't request childAttributes from the correct block
      while (lastChild != null) {
        final IElementType lastType = lastChild.getElementType();
        if (lastType == PyElementTypes.STATEMENT_LIST && hasLineBreaksBeforeInSameParent(lastChild, 1)) {
          if (dedentAfterLastStatement((PyStatementList)lastChild.getPsi())) {
            break;
          }
          statementListsBelow++;
        }
        else if (statementListsBelow > 0 && lastChild.getPsi() instanceof PsiErrorElement) {
          statementListsBelow++;
        }
        if (myNode.getElementType() == PyElementTypes.STATEMENT_LIST && lastChild.getPsi() instanceof PsiErrorElement) {
          return ChildAttributes.DELEGATE_TO_PREV_CHILD;
        }
        lastChild = getLastNonSpaceChild(lastChild, true);
      }
    }

    // HACKETY-HACK
    // If a multi-step dedent follows the cursor position (see testMultiDedent()),
    // the whitespace (which must be a single Py:LINE_BREAK token) gets attached
    // to the outermost indented block (because we may not consume the DEDENT
    // tokens while parsing inner blocks). The policy is to put the indent to
    // the innermost block, so we need to resolve the situation here. Nested
    // delegation sometimes causes NPEs in formatter core, so we calculate the
    // correct indent manually.
    if (statementListsBelow > 0) { // was 1... strange
      @SuppressWarnings("ConstantConditions")
      final int indent = myContext.getSettings().getIndentOptions().INDENT_SIZE;
      return new ChildAttributes(Indent.getSpaceIndent(indent * statementListsBelow), null);
    }

    /*
    // it might be something like "def foo(): # comment" or "[1, # comment"; jump up to the real thing
    if (_node instanceof PsiComment || _node instanceof PsiWhiteSpace) {
      get
    }
    */


    final Indent childIndent = getChildIndent(newChildIndex);
    final Alignment childAlignment = getChildAlignment();
    return new ChildAttributes(childIndent, childAlignment);
  }

  private static boolean dedentAfterLastStatement(@NotNull PyStatementList statementList) {
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length == 0) {
      return false;
    }
    final PyStatement last = statements[statements.length - 1];
    return last instanceof PyReturnStatement || last instanceof PyRaiseStatement || last instanceof PyPassStatement || isEllipsis(last);
  }

  private static boolean isEllipsis(@NotNull PyStatement statement) {
    if (statement instanceof PyExpressionStatement) {
      final PyExpression expression = ((PyExpressionStatement)statement).getExpression();
      if (expression instanceof PyNoneLiteralExpression) {
        return ((PyNoneLiteralExpression)expression).isEllipsis();
      }
    }

    return false;
  }

  @Nullable
  private Alignment getChildAlignment() {
    if (ourListElementTypes.contains(myNode.getElementType())) {
      if (isInControlStatement()) {
        return null;
      }
      if (myNode.getPsi() instanceof PyParameterList && !myContext.getSettings().ALIGN_MULTILINE_PARAMETERS) {
        return null;
      }
      if (myNode.getPsi() instanceof PyDictLiteralExpression) {
        final PyKeyValueExpression lastElement = ArrayUtil.getLastElement(((PyDictLiteralExpression)myNode.getPsi()).getElements());
        if (lastElement == null || lastElement.getValue() == null /* incomplete */) {
          return null;
        }
      }
      return getAlignmentForChildren();
    }
    return null;
  }

  @NotNull
  private Indent getChildIndent(int newChildIndex) {
    final ASTNode afterNode = getAfterNode(newChildIndex);
    final ASTNode lastChild = getLastNonSpaceChild(myNode, false);
    if (lastChild != null && lastChild.getElementType() == PyElementTypes.STATEMENT_LIST && mySubBlocks.size() >= newChildIndex) {
      if (afterNode == null) {
        return Indent.getNoneIndent();
      }

      // handle pressing Enter after colon and before first statement in
      // existing statement list
      if (afterNode.getElementType() == PyElementTypes.STATEMENT_LIST || afterNode.getElementType() == PyTokenTypes.COLON) {
        return Indent.getNormalIndent();
      }

      // handle pressing Enter after colon when there is nothing in the
      // statement list
      final ASTNode lastFirstChild = lastChild.getFirstChildNode();
      if (lastFirstChild != null && lastFirstChild == lastChild.getLastChildNode() && lastFirstChild.getPsi() instanceof PsiErrorElement) {
        return Indent.getNormalIndent();
      }
    }
    else if (lastChild != null && PyElementTypes.LIST_LIKE_EXPRESSIONS.contains(lastChild.getElementType())) {
      // handle pressing enter at the end of a list literal when there's no closing paren or bracket
      final ASTNode lastLastChild = lastChild.getLastChildNode();
      if (lastLastChild != null && lastLastChild.getPsi() instanceof PsiErrorElement) {
        // we're at a place like this: [foo, ... bar, <caret>
        // we'd rather align to foo. this may be not a multiple of tabs.
        final PsiElement expr = lastChild.getPsi();
        PsiElement exprItem = expr.getFirstChild();
        boolean found = false;
        while (exprItem != null) { // find a worthy element to align to
          if (exprItem instanceof PyElement) {
            found = true; // align to foo in "[foo,"
            break;
          }
          if (exprItem instanceof PsiComment) {
            found = true; // align to foo in "[ # foo,"
            break;
          }
          exprItem = exprItem.getNextSibling();
        }
        if (found) {
          final PsiDocumentManager docMgr = PsiDocumentManager.getInstance(exprItem.getProject());
          final Document doc = docMgr.getDocument(exprItem.getContainingFile());
          if (doc != null) {
            int lineNum = doc.getLineNumber(exprItem.getTextOffset());
            final int itemCol = exprItem.getTextOffset() - doc.getLineStartOffset(lineNum);
            final PsiElement hereElt = getNode().getPsi();
            lineNum = doc.getLineNumber(hereElt.getTextOffset());
            final int nodeCol = hereElt.getTextOffset() - doc.getLineStartOffset(lineNum);
            final int padding = itemCol - nodeCol;
            if (padding > 0) { // negative is a syntax error,  but possible
              return Indent.getSpaceIndent(padding);
            }
          }
        }
        return Indent.getContinuationIndent(); // a fallback
      }
    }

    if (afterNode != null && afterNode.getElementType() == PyElementTypes.KEY_VALUE_EXPRESSION) {
      final PyKeyValueExpression keyValue = (PyKeyValueExpression)afterNode.getPsi();
      if (keyValue != null && keyValue.getValue() == null) {  // incomplete
        return Indent.getContinuationIndent();
      }
    }

    final IElementType parentType = myNode.getElementType();
    // constructs that imply indent for their children
    if (parentType == PyElementTypes.PARAMETER_LIST ||
        (parentType == PyElementTypes.ARGUMENT_LIST && myContext.getPySettings().USE_CONTINUATION_INDENT_FOR_ARGUMENTS)) {
      return Indent.getContinuationIndent();
    }
    if (ourListElementTypes.contains(parentType) || myNode.getPsi() instanceof PyStatementPart) {
      return Indent.getNormalIndent();
    }

    if (afterNode != null) {
      ASTNode wsAfter = afterNode.getTreeNext();
      while (wsAfter != null && wsAfter.getElementType() == TokenType.WHITE_SPACE) {
        if (wsAfter.getText().indexOf('\\') >= 0) {
          return Indent.getNormalIndent();
        }
        wsAfter = wsAfter.getTreeNext();
      }
    }
    return Indent.getNoneIndent();
  }

  @Nullable
  private ASTNode getAfterNode(int newChildIndex) {
    if (newChildIndex == 0) {  // block text contains backslash line wrappings, child block list not built
      return null;
    }
    int prevIndex = newChildIndex - 1;
    while (prevIndex > 0 && getSubBlockByIndex(prevIndex).getNode().getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      prevIndex--;
    }
    return getSubBlockByIndex(prevIndex).getNode();
  }

  private static ASTNode getLastNonSpaceChild(@NotNull ASTNode node, boolean acceptError) {
    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null &&
           (lastChild.getElementType() == TokenType.WHITE_SPACE || (!acceptError && lastChild.getPsi() instanceof PsiErrorElement))) {
      lastChild = lastChild.getTreePrev();
    }
    return lastChild;
  }

  @Override
  public boolean isIncomplete() {
    // if there's something following us, we're not incomplete
    if (!PsiTreeUtil.hasErrorElements(myNode.getPsi())) {
      PsiElement element = myNode.getPsi().getNextSibling();
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }
      if (element != null) {
        return false;
      }
    }

    final ASTNode lastChild = getLastNonSpaceChild(myNode, false);
    if (lastChild != null) {
      if (lastChild.getElementType() == PyElementTypes.STATEMENT_LIST) {
        // only multiline statement lists are considered incomplete
        final ASTNode statementListPrev = lastChild.getTreePrev();
        if (statementListPrev != null && statementListPrev.getText().indexOf('\n') >= 0) {
          return true;
        }
      }
      if (lastChild.getElementType() == PyElementTypes.BINARY_EXPRESSION) {
        final PyBinaryExpression binaryExpression = (PyBinaryExpression)lastChild.getPsi();
        if (binaryExpression.getRightExpression() == null) {
          return true;
        }
      }
      if (isIncompleteCall(lastChild)) return true;
    }

    if (myNode.getPsi() instanceof PyArgumentList) {
      final PyArgumentList argumentList = (PyArgumentList)myNode.getPsi();
      return argumentList.getClosingParen() == null;
    }
    if (isIncompleteCall(myNode)) {
      return true;
    }

    return false;
  }

  private static boolean isIncompleteCall(@NotNull ASTNode node) {
    if (node.getElementType() == PyElementTypes.CALL_EXPRESSION) {
      final PyCallExpression callExpression = (PyCallExpression)node.getPsi();
      final PyArgumentList argumentList = callExpression.getArgumentList();
      if (argumentList == null || argumentList.getClosingParen() == null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }
}
