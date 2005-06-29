package com.intellij.psi.formatter.java;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractJavaBlock extends AbstractBlock implements JavaBlock{
  protected final CodeStyleSettings mySettings;
  protected Indent myIndent;
  private Indent myChildIndent;
  private Alignment myChildAlignment;
  private boolean myUseChildAttributes = false;
  private Wrap myAnnotationWrap = null;

  public AbstractJavaBlock(final ASTNode node,
                           final Wrap wrap,
                           final Alignment alignment,
                           final Indent indent,
                           final CodeStyleSettings settings) {
    super(node, wrap, alignment);
    mySettings = settings;
    myIndent = indent;
  }

  public static Block createJavaBlock(final ASTNode child,
                                      final CodeStyleSettings settings,
                                      final Indent indent,
                                      Wrap wrap,
                                      Alignment alignment) {
    Indent actualIndent = indent == null ? getDefaultSubtreeIndent(child) : indent;
    final IElementType elementType = child.getElementType();
    if (child.getPsi() instanceof PsiClass) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings);
    }
    if (isBlockType(elementType)) {
      return new BlockContainingJavaBlock(child, wrap,  alignment, actualIndent, settings);
    }
    if (isStatement(child, child.getTreeParent())) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings);
    }
    if (child instanceof LeafElement) {
      return new LeafBlock(child, wrap, alignment, actualIndent);
    }
    else if (isLikeExtendsList(elementType)) {
      return new ExtendsListBlock(child, wrap, alignment, settings);
    }
    else if (elementType == ElementType.CODE_BLOCK) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings);
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
           || elementType == ElementType.TRY_STATEMENT
           || elementType == ElementType.CATCH_SECTION
           || elementType == ElementType.IF_STATEMENT
           || elementType == ElementType.METHOD
           || elementType == ElementType.ARRAY_INITIALIZER_EXPRESSION
           || elementType == ElementType.FOREACH_STATEMENT;
  }

  public static Block createJavaBlock(final ASTNode child, final CodeStyleSettings settings) {
    return createJavaBlock(child, settings, getDefaultSubtreeIndent(child), null, null);
  }

  private static Indent getDefaultSubtreeIndent(final ASTNode child) {
    final ASTNode parent = child.getTreeParent();
    if (child.getElementType() == ElementType.ANNOTATION) return Formatter.getInstance().getNoneIndent();

    final ASTNode prevElement = getPrevElement(child);
    if (prevElement != null && prevElement.getElementType() == ElementType.MODIFIER_LIST) {
      return Formatter.getInstance().getNoneIndent();
    }

    if (child.getElementType() == ElementType.DOC_TAG) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.DOC_COMMENT_LEADING_ASTERISKS) return Formatter.getInstance().createSpaceIndent(1);
    if (child.getPsi() instanceof PsiFile) return Formatter.getInstance().getNoneIndent();
    if (parent != null) {
      final Indent defaultChildIndent = getChildIndent(parent);
      if (defaultChildIndent != null) return defaultChildIndent;
    }

    return null;
  }

  private static Indent getChildIndent(final ASTNode parent) {
    if (parent.getElementType() == ElementType.MODIFIER_LIST) return  Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.JSP_CODE_BLOCK) return Formatter.getInstance().createNormalIndent();
    if (parent.getElementType() == ElementType.DUMMY_HOLDER) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.CLASS) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.IF_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.TRY_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.CATCH_SECTION) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.FOR_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.FOREACH_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.BLOCK_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.DO_WHILE_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.WHILE_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.SWITCH_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.METHOD) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == JavaDocElementType.DOC_COMMENT) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == JavaDocElementType.DOC_TAG) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == JavaDocElementType.DOC_INLINE_TAG) return Formatter.getInstance().getNoneIndent();
    if (parent.getElementType() == ElementType.IMPORT_LIST) return Formatter.getInstance().getNoneIndent();
    if (SourceTreeToPsiMap.treeElementToPsi(parent) instanceof PsiFile) {
      return Formatter.getInstance().getNoneIndent();
    }
    else {
      return null;
    }
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

  protected static boolean isStatement(final ASTNode child, final ASTNode parentNode) {
    if (parentNode != null) {
      if (parentNode.getElementType() == ElementType.CODE_BLOCK) return false;
      final int role = ((CompositeElement)parentNode).getChildRole(child);
      if (parentNode.getElementType() == ElementType.IF_STATEMENT) return role == ChildRole.THEN_BRANCH || role == ChildRole.ELSE_BRANCH;
      if (parentNode.getElementType() == ElementType.FOR_STATEMENT) return role == ChildRole.LOOP_BODY;
      if (parentNode.getElementType() == ElementType.WHILE_STATEMENT) return role == ChildRole.LOOP_BODY;
      if (parentNode.getElementType() == ElementType.DO_WHILE_STATEMENT) return role == ChildRole.LOOP_BODY;
      if (parentNode.getElementType() == ElementType.FOREACH_STATEMENT) return role == ChildRole.LOOP_BODY;
    }
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
          return Formatter.getInstance().createChildWrap(actualWrap, getWrapType(mySettings.BINARY_OPERATION_WRAP), false);
        } else {
          return actualWrap;
        }
      }
    }
    else if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.TERNARY_OPERATION_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.ASSERT_STATEMENT) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.ASSERT_STATEMENT_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.FOR_STATEMENT_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.METHOD) {
      return Formatter.getInstance().createWrap(getWrapType(mySettings.THROWS_LIST_WRAP), true);
    }
    else if (myNode.getElementType() == ElementType.CODE_BLOCK) {
      return Formatter.getInstance().createWrap(Wrap.NORMAL, true);
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

  protected ASTNode processChild(final ArrayList<Block> result,
                                 ASTNode child,
                                 Alignment defaultAlignment,
                                 final Wrap defaultWrap,
                                 final Indent childIndent) {
    if (child.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
      result.add(createMethodCallExpressiobBlock(child,
                                                 arrangeChildWrap(child, defaultWrap),
                                                 arrangeChildAlignment(child, defaultAlignment)));
    }
    else if (child.getElementType() == ElementType.LBRACE && myNode.getElementType() == ElementType.ARRAY_INITIALIZER_EXPRESSION){
      final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.ARRAY_INITIALIZER_WRAP), false);
      child = processParenBlock(ElementType.LBRACE,
                                ElementType.RBRACE,
                                result,
                                child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.EXPRESSION_LIST){
      final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.CALL_PARAMETERS_WRAP), false);
      child = processParenBlock(result,
                                child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    }

    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARAMETER_LIST){
      final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
      child = processParenBlock(result, child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_PARAMETERS);
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.ANNOTATION_PARAMETER_LIST){
      final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.CALL_PARAMETERS_WRAP), false);
      child = processParenBlock(result, child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARENTH_EXPRESSION){
      child = processParenBlock(result, child,
                                WrappingStrategy.DO_NOT_WRAP,
                                mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    }
    else if (child.getElementType() == ElementType.ENUM_CONSTANT && myNode instanceof ClassElement) {
      child =  processEnumBlock(result, child, ((ClassElement)myNode).findEnumConstantListDelimiterPlace());
    }
    else {
      final Block block =
        createJavaBlock(child, mySettings, childIndent, arrangeChildWrap(child, defaultWrap), arrangeChildAlignment(child, defaultAlignment));

      if (child.getElementType() == ElementType.MODIFIER_LIST && containsAnnotations(child)) {
        myAnnotationWrap = Formatter.getInstance().createWrap(getWrapType(getAnnotationWrapType()), true);
      }

      if (block instanceof AbstractJavaBlock) {
        final AbstractJavaBlock javaBlock = ((AbstractJavaBlock)block);
        if (myNode.getElementType() == ElementType.METHOD_CALL_EXPRESSION && child.getElementType() == ElementType.REFERENCE_EXPRESSION) {
          javaBlock.setReservedWrap(getReservedWrap());
        } else if (myNode.getElementType() == ElementType.REFERENCE_EXPRESSION && child.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
          javaBlock.setReservedWrap(getReservedWrap());
        } else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
          javaBlock.setReservedWrap(defaultWrap);
        } else if (child.getElementType() == ElementType.MODIFIER_LIST) {
          javaBlock.setReservedWrap(myAnnotationWrap);
          if (!lastChildIsAnnotation(child)) {
            myAnnotationWrap = null;
          }
        }
      }

      result.add(block);
    }


    return child;
  }

  private Block createMethodCallExpressiobBlock(final ASTNode node, final Wrap blockWrap, final Alignment alignment) {
    final ArrayList<ASTNode> nodes = new ArrayList<ASTNode>();
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    collectNodes(nodes, node);

    final Wrap wrap = Formatter.getInstance().createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), false);

    while (!nodes.isEmpty()) {
      ArrayList<ASTNode> subNodes = readToNextDot(nodes);
      subBlocks.add(createSynthBlock(subNodes, wrap));
    }

    return new SynteticCodeBlock(subBlocks, alignment, mySettings, Formatter.getInstance().createContinuationWithoutFirstIndent(), blockWrap);
  }

  private Block createSynthBlock(final ArrayList<ASTNode> subNodes, final Wrap wrap)  {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final ASTNode firstNode = subNodes.get(0);
    if (firstNode.getElementType() == ElementType.DOT) {
      subBlocks.add(createJavaBlock(firstNode, getSettings(), Formatter.getInstance().getNoneIndent(),
                                    null,
                                    null));
      subNodes.remove(0);
      if (!subNodes.isEmpty()) {
        subBlocks.add(createSynthBlock(subNodes, wrap));
      }
      return new SynteticCodeBlock(subBlocks, null, mySettings, Formatter.getInstance().createContinuationIndent(), wrap);
    } else {
      return new SynteticCodeBlock(createJavaBlocks(subNodes), null, mySettings,
                                   Formatter.getInstance().createContinuationWithoutFirstIndent(), null);
    }
  }

  private List<Block> createJavaBlocks(final ArrayList<ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<Block>();
    for (ASTNode node : subNodes) {
      result.add(createJavaBlock(node, getSettings(), Formatter.getInstance().createContinuationWithoutFirstIndent(), null, null));
    }
    return result;
  }

  private ArrayList<ASTNode> readToNextDot(final ArrayList<ASTNode> nodes) {
    final ArrayList<ASTNode> result = new ArrayList<ASTNode>();
    result.add(nodes.remove(0));
    for (Iterator<ASTNode> iterator = nodes.iterator(); iterator.hasNext();) {
      ASTNode node = iterator.next();
      if (node.getElementType() == ElementType.DOT) return result;
      result.add(node);
      iterator.remove();
    }
    return result;
  }

  private static void collectNodes(List<ASTNode> nodes, ASTNode node) {
    ChameleonTransforming.transformChildren(node);
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        if (child.getElementType() ==ElementType.METHOD_CALL_EXPRESSION || child.getElementType() == ElementType.REFERENCE_EXPRESSION) {
          collectNodes(nodes, child);
        } else {
          nodes.add(child);
        }
      }
      child = child.getTreeNext();
    }

  }

  private boolean lastChildIsAnnotation(final ASTNode child) {
    ASTNode current = child.getLastChildNode();
    while (current != null && current.getElementType() == ElementType.WHITE_SPACE) {
      current = current.getTreePrev();
    }
    if (current == null) return false;
    return current.getElementType() == ElementType.ANNOTATION;
  }

  private boolean containsAnnotations(final ASTNode child) {
    return ((PsiModifierList)child.getPsi()).getAnnotations().length > 0;
  }

  private int getAnnotationWrapType() {
    if (myNode.getElementType() == ElementType.METHOD) {
      return mySettings.METHOD_ANNOTATION_WRAP;
    }
    if (myNode.getElementType() == ElementType.CLASS) {
      return mySettings.CLASS_ANNOTATION_WRAP;
    }
    if (myNode.getElementType() == ElementType.FIELD) {
      return mySettings.FIELD_ANNOTATION_WRAP;
    }
    if (myNode.getElementType() == ElementType.PARAMETER) {
      return mySettings.PARAMETER_ANNOTATION_WRAP;
    }
    if (myNode.getElementType() == ElementType.LOCAL_VARIABLE) {
      return mySettings.VARIABLE_ANNOTATION_WRAP;
    }
    return CodeStyleSettings.DO_NOT_WRAP;
  }

  protected Alignment arrangeChildAlignment(final ASTNode child, final Alignment defaultAlignment) {
    int role = ((CompositeElement)child.getTreeParent()).getChildRole(child);
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
      if (isAfterClassKeyword(child)) return null;
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

  private boolean isAfterClassKeyword(final ASTNode child) {
    ASTNode treePrev = child.getTreePrev();
    while (treePrev != null) {
      if (treePrev.getElementType() == ElementType.CLASS_KEYWORD ||
        treePrev.getElementType() == ElementType.INTERFACE_KEYWORD) {
        return true;
      }
      treePrev = treePrev.getTreePrev();
    }
    return false;
  }

  private Alignment createAlignment(final boolean alignOption, final Alignment defaultAlignment) {
    return alignOption ?
           (createAlignmentOrDefault(defaultAlignment)) : defaultAlignment;
  }

  protected Wrap arrangeChildWrap(final ASTNode child, Wrap defaultWrap) {
    if (myAnnotationWrap != null) {
      try {
        return myAnnotationWrap;
      }
      finally {
        myAnnotationWrap = null;
      }
    }
    final ASTNode parent = child.getTreeParent();
    int role = ((CompositeElement)parent).getChildRole(child);
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
      }
      else {
        return null;
      }
    }

    else if (myNode.getElementType() == ElementType.MODIFIER_LIST) {
      if (child.getElementType() == ElementType.ANNOTATION) {
        return getReservedWrap();
      }
      ASTNode prevElement = getPrevElement(child);
      if (prevElement != null && prevElement.getElementType() == ElementType.ANNOTATION) {
        return getReservedWrap();
      }
      else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.ASSERT_STATEMENT) {
      if (role == ChildRole.CONDITION) {
        return defaultWrap;
      }
      if (role == ChildRole.ASSERT_DESCRIPTION && !mySettings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE) {
        return defaultWrap;
      }
      if (role == ChildRole.COLON && mySettings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE) {
        return defaultWrap;
      }
      return null;
    }
    else if (myNode.getElementType() == ElementType.CODE_BLOCK) {
      if (role == ChildRole.STATEMENT_IN_BLOCK) {
        return defaultWrap;
      } else {
        return null;
      }
    }
    else {
      return defaultWrap;
    }
  }

  private static ASTNode getPrevElement(final ASTNode child) {
    ASTNode result = child.getTreePrev();
    while (result != null && result.getElementType() == ElementType.WHITE_SPACE) {
      result = result.getTreePrev();
    }
    return result;
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

  private ASTNode processParenBlock(List<Block> result,
                                    ASTNode child,
                                    WrappingStrategy wrappingStrategy,
                                    final boolean doAlign) {

    myUseChildAttributes = true;

    final IElementType from = ElementType.LPARENTH;
    final IElementType to = ElementType.RPARENTH;

    return processParenBlock(from, to, result, child, wrappingStrategy, doAlign);

  }

  private ASTNode processParenBlock(final IElementType from,
                                    final IElementType to, final List<Block> result, ASTNode child,
                                    final WrappingStrategy wrappingStrategy, final boolean doAlign
  ) {
    final Indent externalIndent = Formatter.getInstance().getNoneIndent();
    final Indent internalIndent = Formatter.getInstance().createContinuationIndent();
    AlignmentStrategy alignmentStrategy = AlignmentStrategy.createDoNotAlingCommaStrategy(createAlignment(doAlign, null));
    setChildIndent(internalIndent);
    setChildAlignment(alignmentStrategy.getAlignment(null));

    ASTNode prev = child;
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        if (child.getElementType() == from) {
          result.add(createJavaBlock(child, mySettings, externalIndent, null, null));
        } else if (child.getElementType() == to) {
          result.add(createJavaBlock(child, mySettings, externalIndent, null, FormatterUtil.isAfterIncompleted(child) ? alignmentStrategy.getAlignment(null) : null));
          return child;
        } else {
          final IElementType elementType = child.getElementType();
          result.add(createJavaBlock(child, mySettings, internalIndent,
                                     wrappingStrategy.getWrap(elementType),
                                     alignmentStrategy.getAlignment(elementType)));
          if (to == null) {//process only one statement
            return child;
          }
        }
      }
      prev = child;
      child = child.getTreeNext();
    }

    return prev;
  }

  private ASTNode processEnumBlock(List<Block> result,
                                   ASTNode child,
                                   ASTNode last) {

    final WrappingStrategy wrappingStrategy = WrappingStrategy.createDoNotWrapCommaStrategy(Formatter.getInstance()
      .createWrap(getWrapType(mySettings.ENUM_CONSTANTS_WRAP), true));
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        result.add(createJavaBlock(child, mySettings, Formatter.getInstance().createNormalIndent(),
                                   wrappingStrategy.getWrap(child.getElementType()), null));
        if (child == last) return child;
      }
      child = child.getTreeNext();
    }
    return null;
  }

  private void setChildAlignment(final Alignment alignment) {
    myChildAlignment = alignment;
  }

  private void setChildIndent(final Indent internalIndent) {
    myChildIndent = internalIndent;
  }

  private Alignment createAlignmentOrDefault(final Alignment defaultAlignment) {
    return defaultAlignment == null ? Formatter.getInstance().createAlignment() :defaultAlignment;
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

  protected Indent getCodeBlockInternalIndent(final int baseChildrenIndent) {
    if (isTopLevelClass() && mySettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS) {
      return Formatter.getInstance().getNoneIndent();
    }

    final int braceStyle = getBraceStyle();
    return braceStyle == CodeStyleSettings.NEXT_LINE_SHIFTED ?
           Formatter.getInstance().createNormalIndent(baseChildrenIndent - 1)
           : Formatter.getInstance().createNormalIndent(baseChildrenIndent);
  }

  private boolean isTopLevelClass() {
    if (myNode.getElementType() != ElementType.CLASS) return false;
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getTreeParent()) instanceof PsiFile;
  }

  protected Indent getCodeBlockExternalIndent(){
    final int braceStyle = getBraceStyle();
    if (braceStyle == CodeStyleSettings.END_OF_LINE || braceStyle == CodeStyleSettings.NEXT_LINE || braceStyle == CodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      return Formatter.getInstance().getNoneIndent();
    } else {
      return Formatter.getInstance().createNormalIndent();
    }
  }

  protected abstract Wrap getReservedWrap();

  protected abstract void setReservedWrap(final Wrap reservedWrap);

  protected static ASTNode getTreeNode(final Block child2) {
    if (child2 instanceof JavaBlock) {
      return ((JavaBlock)child2).getFirstTreeNode();
    } else if (child2 instanceof LeafBlock) {
      return ((LeafBlock)child2).getTreeNode();
    } else {
      return null;
    }
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myUseChildAttributes) {
      return new ChildAttributes(myChildIndent, myChildAlignment);
    }
    else if (isAfterJavaDoc(newChildIndex)) {
      return new ChildAttributes(Formatter.getInstance().getNoneIndent(), myChildAlignment);
    }
    else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  protected Indent getChildIndent() {
    return getChildIndent(myNode);
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  protected boolean isAfterJavaDoc(final int newChildIndex) {
    if (newChildIndex == 0) return false;
    final Block previousBlock = getSubBlocks().get(newChildIndex - 1);
    if (!(previousBlock instanceof AbstractBlock)) return false;
    final IElementType previousElementType = ((AbstractBlock)previousBlock).getNode().getElementType();
    return previousElementType ==JavaDocElementType.DOC_COMMENT;
  }
}
