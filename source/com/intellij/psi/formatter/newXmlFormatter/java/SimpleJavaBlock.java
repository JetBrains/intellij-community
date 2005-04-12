package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.*;
import com.intellij.psi.formatter.newXmlFormatter.xml.AbstractBlock;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class SimpleJavaBlock extends AbstractJavaBlock {
  private Wrap myReservedWrap;

  public SimpleJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent,settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    return createBlocksFromChild(myNode.getFirstChildNode());
  }

  private int getBraceStyle() {
    final PsiElement psiNode = SourceTreeToPsiMap.treeElementToPsi(myNode);
    if (psiNode instanceof PsiClass) {
      return mySettings.CLASS_BRACE_STYLE;
    } else if (psiNode instanceof PsiMethod){
      return mySettings.METHOD_BRACE_STYLE;
    } else {
      return mySettings.BRACE_STYLE;
    }

  }

  private ArrayList<Block> createBlocksFromChild(ASTNode child) {
    if (myNode.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
      if (myReservedWrap == null) myReservedWrap = Formatter.getInstance().createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), false);
    }

    final ArrayList<Block> result = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        child = processChild(result, child, childAlignment, childWrap);
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    return result;
  }

  protected Wrap createChildWrap() {
    if (myNode.getElementType() == ElementType.EXTENDS_LIST || myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.EXTENDS_LIST_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
      Wrap actualWrap = myWrap != null ? myWrap : myReservedWrap;
      if (actualWrap == null) {
        return Formatter.getInstance().createWrap(getWrapType(mySettings.BINARY_OPERATION_WRAP), false);
      } else {
        if (!hasTheSamePriority(myNode.getTreeParent())) {
          //Formatter.getInstance().createWrap(getWrapType(mySettings.BINARY_OPERATION_WRAP), false);
          return Formatter.getInstance().createChildWrap(actualWrap, getWrapType(mySettings.BINARY_OPERATION_WRAP), false);
        } else {
          return actualWrap;
        }
      }
    }
    else if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.TERNARY_OPERATION_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.FOR_STATEMENT_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.METHOD) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.THROWS_LIST_WRAP), true);
    }
    else {
      return null;
    }
  }

  protected Alignment createChildAlignment() {
    if (myNode.getElementType() == ElementType.ASSIGNMENT_EXPRESSION){
      return createAlignment(mySettings.ALIGN_MULTILINE_ASSIGNMENT, null);
    } else if (myNode.getElementType() == ElementType.PARENTH_EXPRESSION){
      return createAlignment(mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION, null);
    } else if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION){
      return createAlignment(mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null);
    } else if (myNode.getElementType() == ElementType.FOR_STATEMENT){
      return createAlignment(mySettings.ALIGN_MULTILINE_FOR, null);
    } else if (myNode.getElementType() == ElementType.EXTENDS_LIST){
      return createAlignment(mySettings.ALIGN_MULTILINE_EXTENDS_LIST, null);
    } else if (myNode.getElementType() == ElementType.IMPLEMENTS_LIST){
      return createAlignment(mySettings.ALIGN_MULTILINE_EXTENDS_LIST, null);
    } else if (myNode.getElementType() == ElementType.THROWS_LIST){
      return createAlignment(mySettings.ALIGN_MULTILINE_THROWS_LIST, null);
    } else if (myNode.getElementType() == ElementType.PARAMETER_LIST){
      return createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS, null);
    } else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION){
      Alignment defaultAlignment = null;
      if (shouldInheritAlignment()) {
        defaultAlignment = myAlignment;
      }
      return createAlignment(mySettings.ALIGN_MULTILINE_BINARY_OPERATION, defaultAlignment);
    }
    else if (myNode.getElementType() == ElementType.CLASS) {
      return Formatter.getInstance().createAlignment();
    }
    else if (myNode.getElementType() == ElementType.METHOD) {
      return Formatter.getInstance().createAlignment();
    }

    else {
      return null;
    }
  }

  private boolean shouldInheritAlignment() {
    if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
      final ASTNode treeParent = myNode.getTreeParent();
      if (treeParent != null && treeParent.getElementType() == ElementType.BINARY_EXPRESSION) {
        return hasTheSamePriority(treeParent);
      }
    }
    return false;
  }

  protected ASTNode processChild(final ArrayList<Block> result, ASTNode child, Alignment defaultAlignment, final Wrap defaultWrap) {
    if (child.getElementType() == ElementType.LBRACE) {
      if (!result.isEmpty()) {
        final ArrayList<Block> subBlock = new ArrayList<Block>(result);
        result.clear();
        result.add(new SynteticCodeBlock(subBlock, null, mySettings, null, null));
      }
      child = createBlockFrom(result, child, ElementType.LBRACE, ElementType.RBRACE);
    }
    else if (isStatement(child)) {
      createBlockFrom(result, child, null, null);
    }
    else
    if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.EXPRESSION_LIST){
      final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.CALL_PARAMETERS_WRAP), false);
      child = createSynteticBlock(result, child, ElementType.LPARENTH, ElementType.RPARENTH,
                                  Formatter.getInstance().getNoneIndent(),
                                  Formatter.getInstance().createContinuationIndent(),
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS, null));
    }

    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARAMETER_LIST){
      final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
      child = createSynteticBlock(result, child, ElementType.LPARENTH, ElementType.RPARENTH,
                                  Formatter.getInstance().getNoneIndent(),
                                  Formatter.getInstance().createContinuationIndent(),
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS, null));
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARENTH_EXPRESSION){
      child = createSynteticBlock(result, child, ElementType.LPARENTH, ElementType.RPARENTH,
                                  Formatter.getInstance().getNoneIndent(),
                                  Formatter.getInstance().createContinuationIndent(),
                                  WrappingStrategy.DO_NOT_WRAP,
                                  createAlignment(mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION, null));
    }

    else {
      Indent indent = null;
      if (shouldShift(child)) {
        indent = Formatter.getInstance().createNormalIndent();
      }
      final AbstractJavaBlock javaBlock =
        createJavaBlock(child, mySettings, indent, arrangeChildWrap(child, defaultWrap), arrangeChildAlignment(child, defaultAlignment));

      if (myNode.getElementType() == ElementType.METHOD_CALL_EXPRESSION && child.getElementType() == ElementType.REFERENCE_EXPRESSION) {
        ((SimpleJavaBlock)javaBlock).myReservedWrap = myReservedWrap;
      } else if (myNode.getElementType() == ElementType.REFERENCE_EXPRESSION && child.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
        ((SimpleJavaBlock)javaBlock).myReservedWrap = myReservedWrap;
      } else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
        ((SimpleJavaBlock)javaBlock).myReservedWrap = defaultWrap;
      }

      result.add(javaBlock);
    }


    return child;
  }

  protected boolean isStatement(final ASTNode child) {
    if (myNode.getElementType() == ElementType.CODE_BLOCK) return false;
    if (child.getElementType() == ElementType.BLOCK_STATEMENT) return false;
    final int role = ((CompositeElement)myNode).getChildRole(child);
    if (myNode.getElementType() == ElementType.IF_STATEMENT) return role == ChildRole.THEN_BRANCH || role == ChildRole.ELSE_BRANCH;
    if (myNode.getElementType() == ElementType.FOR_STATEMENT) return role == ChildRole.LOOP_BODY;
    if (myNode.getElementType() == ElementType.WHILE_STATEMENT) return role == ChildRole.LOOP_BODY;
    if (myNode.getElementType() == ElementType.DO_WHILE_STATEMENT) return role == ChildRole.LOOP_BODY;
    if (myNode.getElementType() == ElementType.FOREACH_STATEMENT) return role == ChildRole.LOOP_BODY;
    return false;
  }

  protected Alignment arrangeChildAlignment(final ASTNode child, final Alignment defaultAlignment) {
    int role = ((CompositeElement)myNode).getChildRole(child);
    if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      if (role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE) {
        return defaultAlignment;
      } else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.EXTENDS_LIST || myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST || role == ChildRole.IMPLEMENTS_KEYWORD) {
        return defaultAlignment;
      } else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.THROWS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return defaultAlignment;
      } else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.CLASS) {
      if (role == ChildRole.CLASS_OR_INTERFACE_KEYWORD) return defaultAlignment;
      if (role == ChildRole.MODIFIER_LIST) return defaultAlignment;
      if (role == ChildRole.DOC_COMMENT) return defaultAlignment;
      return null;
    }

    else if (myNode.getElementType() == ElementType.METHOD) {
      if (role == ChildRole.MODIFIER_LIST) return defaultAlignment;
      if (role == ChildRole.TYPE) return defaultAlignment;
      return null;
    }

    else {
      return defaultAlignment;
    }
  }

  private Alignment createAlignment(final boolean alignOption, final Alignment defaultAlignment) {
    return alignOption ?
           (createAlignmentOrDefault(defaultAlignment)) : defaultAlignment;
  }

  private Alignment createAlignmentOrDefault(final Alignment defaultAlignment) {
    return defaultAlignment == null ? Formatter.getInstance().createAlignment() :defaultAlignment;
  }

  protected Wrap arrangeChildWrap(final ASTNode child, Wrap defaultWrap) {
    int role = ((CompositeElement)myNode).getChildRole(child);
    if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
      if (role == ChildRole.OPERATION_SIGN && !mySettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE) return null;
      if (role == ChildRole.ROPERAND && mySettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE) return null;
      return defaultWrap;
    }

    else if (child.getElementType() == ElementType.EXTENDS_LIST || child.getElementType() == ElementType.IMPLEMENTS_LIST) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.EXTENDS_KEYWORD_WRAP), true);
    }
    else if (myNode.getElementType() == ElementType.EXTENDS_LIST || myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return defaultWrap;
      } else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION) {
      if (role == ChildRole.COLON && !mySettings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE) return null;
      if (role == ChildRole.QUEST && !mySettings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE) return null;
      if (role == ChildRole.THEN_EXPRESSION && mySettings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE) return null;
      if (role == ChildRole.ELSE_EXPRESSION && mySettings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE) return null;
      return defaultWrap;

    }
    else if (myNode.getElementType() == ElementType.REFERENCE_EXPRESSION) {
      if (role == ChildRole.DOT) {
        return myReservedWrap;
      } else {
        return defaultWrap;
      }
    }
    else if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      if (role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE) {
        return defaultWrap;
      } else {
        return null;
      }

    }

    else if (myNode.getElementType() == ElementType.METHOD) {
      if (role == ChildRole.THROWS_LIST) {
        return defaultWrap;
      } else {
        return null;
      }
    }

    else {
      return defaultWrap;
    }
  }

  private boolean hasTheSamePriority(final ASTNode node) {
    if (node == null) return false;
    if (node.getElementType() != ElementType.BINARY_EXPRESSION) {
      return false;
    } else {
      final PsiBinaryExpression expr1 = (PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(myNode);
      final PsiBinaryExpression expr2 = (PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(node);
      final PsiJavaToken op1 = expr1.getOperationSign();
      final PsiJavaToken op2 = expr2.getOperationSign();
      return op1.getTokenType() == op2.getTokenType();
    }
  }

  protected int getWrapType(final int wrap) {
    switch(wrap) {
        case Wrap.ALWAYS: return CodeStyleSettings.WRAP_ALWAYS;
        case Wrap.NORMAL: return CodeStyleSettings.WRAP_AS_NEEDED;
        case Wrap.NONE: return CodeStyleSettings.DO_NOT_WRAP;
        default: return CodeStyleSettings.WRAP_ON_EVERY_ITEM;
    }
  }



  private ASTNode createCaseBlockFrom(final List<Block> result, ASTNode child) {
    List<Block> statementsUnderCase = new ArrayList<Block>();
    final AbstractJavaBlock caseBlock = createJavaBlock(child, mySettings);
    child = child.getTreeNext();
    ASTNode prev = child;
    while (child != null) {
      if (!containsWhiteSpacesOnly(child)) {
        if (child.getElementType() == ElementType.RBRACE || child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT) {
          result.add(createCaseBlock(statementsUnderCase, caseBlock));
          return prev;
        } else {
          statementsUnderCase.add(createJavaBlock(child, mySettings));
        }
      }
      prev = child;
      child = child.getTreeNext();
    }
    
    result.add(createCaseBlock(statementsUnderCase, caseBlock));

    return null;
  }

  private Block createCaseBlock(final List<Block> statementsUnderCase, final AbstractJavaBlock caseBlock) {
    Indent indentUnderCase = Formatter.getInstance().createNormalIndent();
    if (statementsUnderCase.size() == 1 && isCodeBlock(statementsUnderCase.get(0))) {
      if (mySettings.BRACE_STYLE == CodeStyleSettings.END_OF_LINE || mySettings.BRACE_STYLE == CodeStyleSettings.NEXT_LINE){
        indentUnderCase = Formatter.getInstance().getNoneIndent();
      }
    }
    final SynteticCodeBlock blockUnderCase = statementsUnderCase.isEmpty() ? null :
      new SynteticCodeBlock(statementsUnderCase, null, mySettings, indentUnderCase, null);
    Block[] caseBlocks = blockUnderCase == null ? new Block[] {caseBlock}: new Block[]{caseBlock, blockUnderCase};
    return new SynteticCodeBlock(Arrays.asList(caseBlocks), null, mySettings, Formatter.getInstance().getNoneIndent(), null);
  }

  private boolean isCodeBlock(final Block block) {
    if (!(block instanceof AbstractBlock)) return false;
    return ((AbstractBlock)block).getTreeNode().getElementType() == ElementType.BLOCK_STATEMENT;
  }

  private boolean shouldShift(final ASTNode child) {
    return
    myNode.getElementType() == ElementType.IF_STATEMENT
    && child.getElementType() == ElementType.IF_STATEMENT
    && !mySettings.SPECIAL_ELSE_IF_TREATMENT;
  }

  private ASTNode createSynteticBlock(List<Block> result,
                                      ASTNode child,
                                      IElementType from,
                                      IElementType to,
                                      Indent externalIndent,
                                      Indent internalIndent,
                                      WrappingStrategy wrappingStrategy,
                                      Alignment alignment) {
    ASTNode prev = child;
    List<Block> resultList = new ArrayList<Block>();
    List<Block> codeBlock = new ArrayList<Block>();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        if (child.getElementType() == from) {
          resultList.add(createJavaBlock(child, mySettings));
        } else if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT){
          child = createCaseBlockFrom(codeBlock, child);
        } else if (child.getElementType() == to) {
          if (!codeBlock.isEmpty()) {
            resultList.add(new SynteticCodeBlock(codeBlock, null, mySettings, internalIndent, null));

          }
          resultList.add(createJavaBlock(child, mySettings));
          result.add(new SynteticCodeBlock(resultList, null, mySettings, externalIndent, null));
          return child;
        } else {
          codeBlock.add(createJavaBlock(child, mySettings, null, wrappingStrategy.getWrap(child.getElementType()), alignment));
          if (to == null) {
            resultList.add(new SynteticCodeBlock(codeBlock, null, mySettings, internalIndent, null));
            result.add(new SynteticCodeBlock(resultList, null, mySettings, externalIndent, null));
            return child;//process only one statement
          }
        }
      }
      prev = child;
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    if (!codeBlock.isEmpty()) {
      resultList.add(new SynteticCodeBlock(codeBlock, null, mySettings, internalIndent, null));
    }

    if (!resultList.isEmpty()) {
      result.add(new SynteticCodeBlock(resultList, null, mySettings, externalIndent, null));
    }

    return prev;

  }

  private ASTNode createBlockFrom(List<Block> result, ASTNode child, final IElementType from, final IElementType to) {

    return createSynteticBlock(result,
                               child,
                               from,
                               to,
                               getCodeBlockExternalIndent(),
                               getCodeBlockInternalIndent(child),
                               new WrappingStrategy(Formatter.getInstance().createWrap(Wrap.NORMAL, true)) {
      protected boolean shouldWrap(final IElementType type) {
        return true;
      }
    },
                               null);
  }

  private Indent getCodeBlockInternalIndent(final ASTNode child) {
    if (isTopLevelClass() && mySettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS) {
      return Formatter.getInstance().getNoneIndent();
    }
    final int braceStyle = getBraceStyle();
    Indent indent = braceStyle == CodeStyleSettings.NEXT_LINE_SHIFTED ? Formatter.getInstance().getNoneIndent() : Formatter.getInstance().createNormalIndent();

    if (child.getElementType() == ElementType.IF_STATEMENT && myNode.getElementType() == ElementType.IF_STATEMENT && mySettings.SPECIAL_ELSE_IF_TREATMENT){
      indent = Formatter.getInstance().getNoneIndent();
    }
    return indent;
  }

  private boolean isTopLevelClass() {
    if (myNode.getElementType() != ElementType.CLASS) return false;
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getTreeParent()) instanceof PsiFile;
  }

  private Indent getCodeBlockExternalIndent(){
    final int braceStyle = getBraceStyle();
    if (braceStyle == CodeStyleSettings.END_OF_LINE || braceStyle == CodeStyleSettings.NEXT_LINE) {
      return Formatter.getInstance().getNoneIndent();
    } else {
      return Formatter.getInstance().createNormalIndent();
    }
  }
}
