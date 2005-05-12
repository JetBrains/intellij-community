package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.newXmlFormatter.xml.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.codeFormatting.general.FormatterUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractJavaBlock extends AbstractBlock implements JavaBlock{
  protected final CodeStyleSettings mySettings;
  protected Indent myIndent;
  private Indent myChildIndent;
  private Alignment myChildAlignment;
  private boolean myUseChildAttributes = false;

  public AbstractJavaBlock(final ASTNode node,
                           final Wrap wrap,
                           final Alignment alignment,
                           final Indent indent,
                           final CodeStyleSettings settings) {
    super(node, wrap, alignment);
    mySettings = settings;
    myIndent = indent;
  }

  public static Block createJavaBlock(final ASTNode child, final CodeStyleSettings settings, final Indent indent, Wrap wrap, Alignment alignment) {
    Indent actualIndent = indent == null ? getDefaultIndent(child) : indent;
    final IElementType elementType = child.getElementType();
    if (child instanceof LeafElement) {
      return new LeafBlock(child, wrap, alignment);
    }
    else if (isLikeExtendsList(elementType)) {
      return new ExtendsListBlock(child, wrap, alignment, actualIndent, settings);
    }
    else if (isBlockType(elementType)) {
      return new BlockContainingJavaBlock(child, wrap,  alignment, actualIndent, settings);
    }
    else if (elementType == ElementType.LABELED_STATEMENT) {
      return new LabeledJavaBlock(child, wrap, alignment, actualIndent,settings);
    }
    else if (elementType == JavaDocElementType.DOC_COMMENT) {
      return new DocCommentBlock(child, wrap, alignment, actualIndent, settings);
    }
    else {
      return new SimpleJavaBlock(child, wrap, alignment, actualIndent,settings);
    }
  }

  private static boolean isLikeExtendsList(final IElementType elementType) {
    return elementType == ElementType.EXTENDS_LIST
    || elementType == ElementType.IMPLEMENTS_LIST
    || elementType == ElementType.THROWS_LIST;
  }

  private static boolean isBlockType(final IElementType elementType) {
    return elementType == ElementType.SWITCH_STATEMENT
    || elementType == ElementType.FOR_STATEMENT
    || elementType == ElementType.WHILE_STATEMENT
    || elementType == ElementType.DO_WHILE_STATEMENT
    || elementType == ElementType.IF_STATEMENT
    || elementType == ElementType.METHOD
    || elementType == ElementType.FOREACH_STATEMENT;
  }

  public static Block createJavaBlock(final ASTNode child, final CodeStyleSettings settings) {
    return createJavaBlock(child, settings, getDefaultIndent(child), null, null);
  }

  private static Indent getDefaultIndent(final ASTNode child) {
    if (child.getElementType() == ElementType.JSP_CODE_BLOCK) return Formatter.getInstance().createNormalIndent();
    if (child.getElementType() == ElementType.DUMMY_HOLDER) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.CLASS) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.IF_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.TRY_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.CATCH_SECTION) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.FOR_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.FOREACH_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.BLOCK_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.DO_WHILE_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.WHILE_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.SWITCH_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.METHOD) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == JavaDocElementType.DOC_COMMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == JavaDocElementType.DOC_TAG) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == JavaDocElementType.DOC_INLINE_TAG) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.IMPORT_LIST) return Formatter.getInstance().getNoneIndent();
    if (SourceTreeToPsiMap.treeElementToPsi(child) instanceof PsiFile) return Formatter.getInstance().getNoneIndent();
    return null;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    return new JavaSpacePropertyProcessor(AbstractJavaBlock.getTreeNode(child2), mySettings).getResult();
  }

  public ASTNode getFirstTreeNode() {
    return myNode;
  }

  public Indent getIndent() {
    return myIndent;
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

  protected abstract List<Block> buildChildren();

  protected Wrap createChildWrap() {
    if (myNode.getElementType() == ElementType.EXTENDS_LIST || myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.EXTENDS_LIST_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
      Wrap actualWrap = myWrap != null ? myWrap : getReservedWrap();
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
                                  AlignmentStrategy.createDoNotAlingCommaStrategy(createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS, null))
                                  );
    }

    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARAMETER_LIST){
      final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
      child = createSynteticBlock(result, child, ElementType.LPARENTH, ElementType.RPARENTH,
                                  Formatter.getInstance().getNoneIndent(),
                                  Formatter.getInstance().createContinuationIndent(),
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  AlignmentStrategy.createDoNotAlingCommaStrategy(createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS, null)));
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARENTH_EXPRESSION){
      child = createSynteticBlock(result, child, ElementType.LPARENTH, ElementType.RPARENTH,
                                  Formatter.getInstance().getNoneIndent(),
                                  Formatter.getInstance().createContinuationIndent(),
                                  WrappingStrategy.DO_NOT_WRAP,
                                  AlignmentStrategy.createDoNotAlingCommaStrategy(createAlignment(mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION, null)));
    }

    else {
      Indent indent = null;
      if (shouldShift(child)) {
        indent = Formatter.getInstance().createNormalIndent();
      }
      final Block block =
        createJavaBlock(child, mySettings, indent, arrangeChildWrap(child, defaultWrap), arrangeChildAlignment(child, defaultAlignment));

      if (block instanceof AbstractJavaBlock) {
        final AbstractJavaBlock javaBlock = ((AbstractJavaBlock)block);
        if (myNode.getElementType() == ElementType.METHOD_CALL_EXPRESSION && child.getElementType() == ElementType.REFERENCE_EXPRESSION) {
          javaBlock.setReservedWrap(getReservedWrap());
        } else if (myNode.getElementType() == ElementType.REFERENCE_EXPRESSION && child.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
          javaBlock.setReservedWrap(getReservedWrap());
        } else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
          javaBlock.setReservedWrap(defaultWrap);
        }
      }

      result.add(block);
    }


    return child;
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
    else if (child.getElementType() == ElementType.THROWS_LIST) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.THROWS_KEYWORD_WRAP), true);
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
        return getReservedWrap();
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
                                      AlignmentStrategy alignmentStrategy) {
    myUseChildAttributes = true;
    setChildIndent(internalIndent);
    setChildAlignment(alignmentStrategy.getAlignment(null));

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
          resultList.add(createJavaBlock(child, mySettings, null, null, FormatterUtil.isAfterIncompleted(child) ? alignmentStrategy.getAlignment(null) : null));
          final SynteticCodeBlock externalBlock = new SynteticCodeBlock(resultList, null, mySettings, externalIndent, null);
          result.add(externalBlock);
          externalBlock.setChildIndent(internalIndent);
          externalBlock.setChildAlignment(alignmentStrategy.getAlignment(null));
          return child;
        } else {
          final IElementType elementType = child.getElementType();
          codeBlock.add(createJavaBlock(child, mySettings, null,
                                        wrappingStrategy.getWrap(elementType),
                                        alignmentStrategy.getAlignment(elementType)));
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

  private void setChildAlignment(final Alignment alignment) {
    myChildAlignment = alignment;
  }

  private void setChildIndent(final Indent internalIndent) {
    myChildIndent = internalIndent;
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
                               AlignmentStrategy.DO_NOT_ALIGN);
  }

  private Alignment createAlignmentOrDefault(final Alignment defaultAlignment) {
    return defaultAlignment == null ? Formatter.getInstance().createAlignment() :defaultAlignment;
  }

  private ASTNode createCaseBlockFrom(final List<Block> result, ASTNode child) {
    List<Block> statementsUnderCase = new ArrayList<Block>();
    final Block caseBlock = createJavaBlock(child, mySettings);
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

  private Block createCaseBlock(final List<Block> statementsUnderCase, final Block caseBlock) {
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

  private Indent getCodeBlockInternalIndent(final ASTNode child) {
    if (isTopLevelClass() && mySettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS) {
      return Formatter.getInstance().getNoneIndent();
    }

    if (myNode.getTreeParent().getElementType() == ElementType.SWITCH_STATEMENT && !mySettings.INDENT_CASE_FROM_SWITCH) {
      return Formatter.getInstance().getNoneIndent();
    }

    if (child.getElementType() == ElementType.IF_STATEMENT && myNode.getElementType() == ElementType.IF_STATEMENT && mySettings.SPECIAL_ELSE_IF_TREATMENT){
      return Formatter.getInstance().getNoneIndent();
    }

    final int braceStyle = getBraceStyle();
    return braceStyle == CodeStyleSettings.NEXT_LINE_SHIFTED ? Formatter.getInstance().getNoneIndent()
           : Formatter.getInstance().createNormalIndent();
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

  protected abstract Wrap getReservedWrap();

  protected abstract void setReservedWrap(final Wrap reservedWrap);

  protected static ASTNode getTreeNode(final Block child2) {
    if (child2 instanceof AbstractBlock) {
      return ((JavaBlock)child2).getFirstTreeNode();
    } else if (child2 instanceof LeafBlock) {
      return ((LeafBlock)child2).getTreeNode();
    } else if (child2 instanceof SynteticCodeBlock) {
      return ((SynteticCodeBlock)child2).getFirstTreeNode();
    } else {
      return null;
    }
  }

  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myUseChildAttributes) {
      return new ChildAttributes(myChildIndent, myChildAlignment);
    } else {
      return super.getChildAttributes(newChildIndex);
    }
  }
}
