package com.intellij.psi.formatter.java;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractJavaBlock extends AbstractBlock implements JavaBlock {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.java.AbstractJavaBlock");

  protected final CodeStyleSettings mySettings;
  protected Indent myIndent;
  protected Indent myChildIndent;
  protected Alignment myChildAlignment;
  protected boolean myUseChildAttributes = false;
  private boolean myIsAfterClassKeyword = false;
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
    if (child.getPsi() instanceof PsiWhiteSpace) {
      String text = child.getText();
      int start = CharArrayUtil.shiftForward(text, 0, " \t\n");
      int end = CharArrayUtil.shiftBackward(text, text.length() - 1, " \t\n") + 1;
      LOG.assertTrue(start < end);
      return new PartialWhitespaceBlock(child, new TextRange(start + child.getStartOffset(), end + child.getStartOffset()),
                                        wrap, alignment, actualIndent, settings);
    }
    if (child.getPsi() instanceof PsiClass) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings);
    }
    if (isBlockType(elementType)) {
      return new BlockContainingJavaBlock(child, wrap, alignment, actualIndent, settings);
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
      return new LabeledJavaBlock(child, wrap, alignment, actualIndent, settings);
    }
    else if (elementType == JavaDocElementType.DOC_COMMENT) {
      return new DocCommentBlock(child, wrap, alignment, actualIndent, settings);
    }
    else {
      return new SimpleJavaBlock(child, wrap, alignment, actualIndent, settings);
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
           || elementType == ElementType.CLASS_INITIALIZER
           || elementType == ElementType.SYNCHRONIZED_STATEMENT
           || elementType == ElementType.FOREACH_STATEMENT;
  }

  public static Block createJavaBlock(final ASTNode child, final CodeStyleSettings settings) {
    return createJavaBlock(child, settings, getDefaultSubtreeIndent(child), null, null);
  }

  @Nullable
  private static Indent getDefaultSubtreeIndent(final ASTNode child) {
    final ASTNode parent = child.getTreeParent();
    if (child.getElementType() == ElementType.ANNOTATION) return Indent.getNoneIndent();

    final ASTNode prevElement = getPrevElement(child);
    if (prevElement != null && prevElement.getElementType() == ElementType.MODIFIER_LIST) {
      return Indent.getNoneIndent();
    }

    if (child.getElementType() == ElementType.DOC_TAG) return Indent.getNoneIndent();
    if (child.getElementType() == ElementType.DOC_COMMENT_LEADING_ASTERISKS) return Indent.getSpaceIndent(1);
    if (child.getPsi() instanceof PsiFile) return Indent.getNoneIndent();
    if (parent != null) {
      final Indent defaultChildIndent = getChildIndent(parent);
      if (defaultChildIndent != null) return defaultChildIndent;
    }

    return null;
  }

  @Nullable
  private static Indent getChildIndent(final ASTNode parent) {
    if (parent.getElementType() == ElementType.MODIFIER_LIST) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.JSP_CODE_BLOCK) return Indent.getNormalIndent();
    if (parent.getElementType() == ElementType.DUMMY_HOLDER) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.CLASS) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.IF_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.TRY_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.CATCH_SECTION) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.FOR_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.FOREACH_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.BLOCK_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.DO_WHILE_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.WHILE_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.SWITCH_STATEMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.METHOD) return Indent.getNoneIndent();
    if (parent.getElementType() == JavaDocElementType.DOC_COMMENT) return Indent.getNoneIndent();
    if (parent.getElementType() == JavaDocElementType.DOC_TAG) return Indent.getNoneIndent();
    if (parent.getElementType() == JavaDocElementType.DOC_INLINE_TAG) return Indent.getNoneIndent();
    if (parent.getElementType() == ElementType.IMPORT_LIST) return Indent.getNoneIndent();
    if (SourceTreeToPsiMap.treeElementToPsi(parent) instanceof PsiFile) {
      return Indent.getNoneIndent();
    }
    else {
      return null;
    }
  }

  public Spacing getSpacing(Block child1, Block child2) {
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

  @Nullable
  protected Wrap createChildWrap() {
    if (myNode.getElementType() == ElementType.EXTENDS_LIST || myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      return Wrap.createWrap(getWrapType(mySettings.EXTENDS_LIST_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
      Wrap actualWrap = myWrap != null ? myWrap : getReservedWrap();
      if (actualWrap == null) {
        return Wrap.createWrap(getWrapType(mySettings.BINARY_OPERATION_WRAP), false);
      }
      else {
        if (!hasTheSamePriority(myNode.getTreeParent())) {
          return Wrap.createChildWrap(actualWrap, getWrapType(mySettings.BINARY_OPERATION_WRAP), false);
        }
        else {
          return actualWrap;
        }
      }
    }
    else if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION) {
      return Wrap.createWrap(getWrapType(mySettings.TERNARY_OPERATION_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.ASSERT_STATEMENT) {
      return Wrap.createWrap(getWrapType(mySettings.ASSERT_STATEMENT_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      return Wrap.createWrap(getWrapType(mySettings.FOR_STATEMENT_WRAP), false);
    }
    else if (myNode.getElementType() == ElementType.THROWS_LIST) {
      return Wrap.createWrap(getWrapType(mySettings.THROWS_LIST_WRAP), true);
    }
    else if (myNode.getElementType() == ElementType.CODE_BLOCK) {
      return Wrap.createWrap(Wrap.NONE, false);
    }
    else if (isAssignment()) {
      return Wrap.createWrap(getWrapType(mySettings.ASSIGNMENT_WRAP), true);
    }
    else {
      return null;
    }
  }

  private boolean isAssignment() {
    return myNode.getElementType() == ElementType.ASSIGNMENT_EXPRESSION || myNode.getElementType() == ElementType.LOCAL_VARIABLE
           || myNode.getElementType() == ElementType.FIELD;
  }

  @Nullable
  protected Alignment createChildAlignment() {
    if (myNode.getElementType() == ElementType.ASSIGNMENT_EXPRESSION) {
      if (myNode.getTreeParent() != null
          && myNode.getTreeParent().getElementType() == ElementType.ASSIGNMENT_EXPRESSION
          && myAlignment != null) {
        return myAlignment;
      }
      else {
        return createAlignment(mySettings.ALIGN_MULTILINE_ASSIGNMENT, null);
      }
    }
    else if (myNode.getElementType() == ElementType.PARENTH_EXPRESSION) {
      return createAlignment(mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION, null);
    }
    else if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION) {
      return createAlignment(mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null);
    }
    else if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      return createAlignment(mySettings.ALIGN_MULTILINE_FOR, null);
    }
    else if (myNode.getElementType() == ElementType.EXTENDS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_EXTENDS_LIST, null);
    }
    else if (myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_EXTENDS_LIST, null);
    }
    else if (myNode.getElementType() == ElementType.THROWS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_THROWS_LIST, null);
    }
    else if (myNode.getElementType() == ElementType.PARAMETER_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS, null);
    }
    else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
      Alignment defaultAlignment = null;
      if (shouldInheritAlignment()) {
        defaultAlignment = myAlignment;
      }
      return createAlignment(mySettings.ALIGN_MULTILINE_BINARY_OPERATION, defaultAlignment);
    }
    else if (myNode.getElementType() == ElementType.CLASS) {
      return Alignment.createAlignment();
    }
    else if (myNode.getElementType() == ElementType.METHOD) {
      return Alignment.createAlignment();
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
    if (child.getElementType() == ElementType.CLASS_KEYWORD || child.getElementType() == ElementType.INTERFACE_KEYWORD) {
      myIsAfterClassKeyword = true;
    }
    if (child.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
      result.add(createMethodCallExpressiobBlock(child,
                                                 arrangeChildWrap(child, defaultWrap),
                                                 arrangeChildAlignment(child, defaultAlignment)));
    }
    else if (child.getElementType() == ElementType.LBRACE && myNode.getElementType() == ElementType.ARRAY_INITIALIZER_EXPRESSION) {
      final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.ARRAY_INITIALIZER_WRAP), false);
      child = processParenBlock(ElementType.LBRACE,
                                ElementType.RBRACE,
                                result,
                                child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.EXPRESSION_LIST) {
      final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.CALL_PARAMETERS_WRAP), false);
      if (mySettings.PREFER_PARAMETERS_WRAP) {
        wrap.ignoreParentWraps();
      }
      child = processParenBlock(result,
                                child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    }

    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARAMETER_LIST) {
      final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
      child = processParenBlock(result, child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_PARAMETERS);
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.ANNOTATION_PARAMETER_LIST) {
      final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.CALL_PARAMETERS_WRAP), false);
      child = processParenBlock(result, child,
                                WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    }
    else if (child.getElementType() == ElementType.LPARENTH && myNode.getElementType() == ElementType.PARENTH_EXPRESSION) {
      child = processParenBlock(result, child,
                                WrappingStrategy.DO_NOT_WRAP,
                                mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    }
    else if (child.getElementType() == ElementType.ENUM_CONSTANT && myNode instanceof ClassElement) {
      child = processEnumBlock(result, child, ((ClassElement)myNode).findEnumConstantListDelimiterPlace());
    }
    else if (mySettings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE && isTernaryOperationSign(child)) {
      child = processTernaryOperationRange(result, child, defaultAlignment, defaultWrap, childIndent);
    }
    else if (child.getElementType() == ElementType.FIELD) {
      child = processField(result, child, defaultAlignment, defaultWrap, childIndent);
    }
    else {
      final Block block =
        createJavaBlock(child, mySettings, childIndent, arrangeChildWrap(child, defaultWrap),
                        arrangeChildAlignment(child, defaultAlignment));

      if (child.getElementType() == ElementType.MODIFIER_LIST && containsAnnotations(child)) {
        myAnnotationWrap = Wrap.createWrap(getWrapType(getAnnotationWrapType()), true);
      }

      if (block instanceof AbstractJavaBlock) {
        final AbstractJavaBlock javaBlock = ((AbstractJavaBlock)block);
        if (myNode.getElementType() == ElementType.METHOD_CALL_EXPRESSION && child.getElementType() == ElementType.REFERENCE_EXPRESSION) {
          javaBlock.setReservedWrap(getReservedWrap());
        }
        else if (myNode.getElementType() == ElementType.REFERENCE_EXPRESSION &&
                 child.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
          javaBlock.setReservedWrap(getReservedWrap());
        }
        else if (myNode.getElementType() == ElementType.BINARY_EXPRESSION) {
          javaBlock.setReservedWrap(defaultWrap);
        }
        else if (child.getElementType() == ElementType.MODIFIER_LIST) {
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

  private ASTNode processField(final ArrayList<Block> result, ASTNode child, final Alignment defaultAlignment, final Wrap defaultWrap,
                               final Indent childIndent) {
    ASTNode lastFieldInGroup = findLastFieldInGroup(child);
    if (lastFieldInGroup == child) {
      result.add(createJavaBlock(child, getSettings(), childIndent, arrangeChildWrap(child, defaultWrap),
                                 arrangeChildAlignment(child, defaultAlignment)));
      return child;
    }
    else {
      final ArrayList<Block> localResult = new ArrayList<Block>();
      while (child != null) {
        if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
          localResult.add(createJavaBlock(child, getSettings(), Indent.getContinuationWithoutFirstIndent(), arrangeChildWrap(child, defaultWrap),
                                          arrangeChildAlignment(child, defaultAlignment)));
        }
        if (child == lastFieldInGroup) break;

        child = child.getTreeNext();

      }
      if (!localResult.isEmpty()) {
        result.add(new SyntheticCodeBlock(localResult, null, getSettings(), childIndent, null));
      }
      return lastFieldInGroup;
    }
  }

  @NotNull private static ASTNode findLastFieldInGroup(final ASTNode child) {
    final PsiTypeElement typeElement = ((PsiVariable)child.getPsi()).getTypeElement();
    if (typeElement == null) return child;

    ASTNode lastChildNode = child.getLastChildNode();
    if (lastChildNode == null) return child;

    if (lastChildNode.getElementType() == ElementType.SEMICOLON) return child;

    ASTNode currentResult = child;
    ASTNode currentNode = child.getTreeNext();

    while (currentNode != null) {
      if (currentNode.getElementType() == ElementType.WHITE_SPACE
          || currentNode.getElementType() == ElementType.COMMA
          || ElementType.COMMENT_BIT_SET.contains(currentNode.getElementType())) {
      }
      else if (currentNode.getElementType() == ElementType.FIELD) {
        if (((PsiVariable)currentNode.getPsi()).getTypeElement() != typeElement) {
          return currentResult;
        }
        else {
          currentResult = currentNode;
        }
      }
      else {
        return currentResult;
      }

      currentNode = currentNode.getTreeNext();
    }
    return currentResult;
  }

  private ASTNode processTernaryOperationRange(final ArrayList<Block> result,
                                               final ASTNode child,
                                               final Alignment defaultAlignment,
                                               final Wrap defaultWrap, final Indent childIndent) {
    final ArrayList<Block> localResult = new ArrayList<Block>();
    final Wrap wrap = arrangeChildWrap(child, defaultWrap);
    final Alignment alignment = arrangeChildAlignment(child, defaultAlignment);
    localResult.add(new LeafBlock(child, wrap, alignment, childIndent));

    ASTNode current = child.getTreeNext();
    while (current != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(current) && current.getTextLength() > 0) {
        if (isTernaryOperationSign(current)) break;
        current = processChild(localResult, current, defaultAlignment, defaultWrap, childIndent);
      }
      if (current != null) {
        current = current.getTreeNext();
      }
    }

    result.add(new SyntheticCodeBlock(localResult, alignment, getSettings(), null, wrap));

    if (current == null) {
      return null;
    }
    else {
      return current.getTreePrev();
    }
  }

  private boolean isTernaryOperationSign(final ASTNode child) {
    if (myNode.getElementType() != ElementType.CONDITIONAL_EXPRESSION) return false;
    final int role = ((CompositeElement)child.getTreeParent()).getChildRole(child);
    return role == ChildRole.OPERATION_SIGN || role == ChildRole.COLON;
  }

  private Block createMethodCallExpressiobBlock(final ASTNode node, final Wrap blockWrap, final Alignment alignment) {
    final ArrayList<ASTNode> nodes = new ArrayList<ASTNode>();
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    collectNodes(nodes, node);

    final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), false);

    while (!nodes.isEmpty()) {
      ArrayList<ASTNode> subNodes = readToNextDot(nodes);
      subBlocks.add(createSynthBlock(subNodes, wrap));
    }

    return new SyntheticCodeBlock(subBlocks, alignment, mySettings, Indent.getContinuationWithoutFirstIndent(),
                                  blockWrap);
  }

  private Block createSynthBlock(final ArrayList<ASTNode> subNodes, final Wrap wrap) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final ASTNode firstNode = subNodes.get(0);
    if (firstNode.getElementType() == ElementType.DOT) {
      subBlocks.add(createJavaBlock(firstNode, getSettings(), Indent.getNoneIndent(),
                                    null,
                                    null));
      subNodes.remove(0);
      if (!subNodes.isEmpty()) {
        subBlocks.add(createSynthBlock(subNodes, wrap));
      }
      return new SyntheticCodeBlock(subBlocks, null, mySettings, Indent.getContinuationIndent(), wrap);
    }
    else {
      return new SyntheticCodeBlock(createJavaBlocks(subNodes), null, mySettings,
                                    Indent.getContinuationWithoutFirstIndent(), null);
    }
  }

  private List<Block> createJavaBlocks(final ArrayList<ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<Block>();
    for (ASTNode node : subNodes) {
      result.add(createJavaBlock(node, getSettings(), Indent.getContinuationWithoutFirstIndent(), null, null));
    }
    return result;
  }

  private static ArrayList<ASTNode> readToNextDot(final ArrayList<ASTNode> nodes) {
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
        if (child.getElementType() == ElementType.METHOD_CALL_EXPRESSION || child.getElementType() == ElementType.REFERENCE_EXPRESSION) {
          collectNodes(nodes, child);
        }
        else {
          nodes.add(child);
        }
      }
      child = child.getTreeNext();
    }

  }

  private static boolean lastChildIsAnnotation(final ASTNode child) {
    ASTNode current = child.getLastChildNode();
    while (current != null && current.getElementType() == ElementType.WHITE_SPACE) {
      current = current.getTreePrev();
    }
    if (current == null) return false;
    return current.getElementType() == ElementType.ANNOTATION;
  }

  private static boolean containsAnnotations(final ASTNode child) {
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

  @Nullable
  protected Alignment arrangeChildAlignment(final ASTNode child, final Alignment defaultAlignment) {
    int role = ((CompositeElement)child.getTreeParent()).getChildRole(child);
    if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      if (role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.EXTENDS_LIST || myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST || role == ChildRole.IMPLEMENTS_KEYWORD) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.THROWS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.CLASS) {
      if (role == ChildRole.CLASS_OR_INTERFACE_KEYWORD) return defaultAlignment;
      if (myIsAfterClassKeyword) return null;
      if (role == ChildRole.MODIFIER_LIST) return defaultAlignment;
      if (role == ChildRole.DOC_COMMENT) return defaultAlignment;
      return null;
    }

    else if (myNode.getElementType() == ElementType.METHOD) {
      if (role == ChildRole.MODIFIER_LIST) return defaultAlignment;
      if (role == ChildRole.TYPE) return defaultAlignment;
      return null;
    }

    else if (myNode.getElementType() == ElementType.ASSIGNMENT_EXPRESSION) {
      if (role == ChildRole.LOPERAND) return defaultAlignment;
      if (role == ChildRole.ROPERAND && child.getElementType() == ElementType.ASSIGNMENT_EXPRESSION) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }

    else {
      return defaultAlignment;
    }
  }

  /*
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

  */
  private static Alignment createAlignment(final boolean alignOption, final Alignment defaultAlignment) {
    return alignOption ?
           (createAlignmentOrDefault(defaultAlignment)) : defaultAlignment;
  }

  @Nullable
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
      return Wrap.createWrap(getWrapType(mySettings.EXTENDS_KEYWORD_WRAP), true);
    }
    else if (child.getElementType() == ElementType.THROWS_LIST) {
      return Wrap.createWrap(getWrapType(mySettings.THROWS_KEYWORD_WRAP), true);
    }
    else if (myNode.getElementType() == ElementType.EXTENDS_LIST || myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return defaultWrap;
      }
      else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.THROWS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return defaultWrap;
      }
      else {
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

    else if (isAssignment()) {
      if (role == ChildRole.INITIALIZER_EQ && mySettings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE) return defaultWrap;
      if (role == ChildRole.OPERATION_SIGN && mySettings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE) return defaultWrap;
      if (role == ChildRole.INITIALIZER && !mySettings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE) return defaultWrap;
      if (role == ChildRole.ROPERAND && !mySettings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE) return defaultWrap;
      return null;
    }

    else if (myNode.getElementType() == ElementType.REFERENCE_EXPRESSION) {
      if (role == ChildRole.DOT) {
        return getReservedWrap();
      }
      else {
        return defaultWrap;
      }
    }
    else if (myNode.getElementType() == ElementType.FOR_STATEMENT) {
      if (role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE) {
        return defaultWrap;
      }
      if (role == ChildRole.LOOP_BODY) {
        return Wrap.createWrap(WrapType.NORMAL, true);
      }
      else {
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
      }
      else {
        return null;
      }
    }

    else if (myNode.getElementType() == ElementType.IF_STATEMENT) {
      if (role == ChildRole.THEN_BRANCH || role == ChildRole.ELSE_BRANCH) {
        if (child.getElementType() == ElementType.BLOCK_STATEMENT) {
          return null;
        }
        else {
          return Wrap.createWrap(WrapType.NORMAL, true);
        }
      }
    }

    else if (myNode.getElementType() == ElementType.FOREACH_STATEMENT || myNode.getElementType() == ElementType.WHILE_STATEMENT) {
      if (role == ChildRole.LOOP_BODY) {
        if (child.getElementType() == ElementType.BLOCK_STATEMENT) {
          return null;
        }
        else {
          return Wrap.createWrap(WrapType.NORMAL, true);
        }
      }
    }

    else if (myNode.getElementType() == ElementType.DO_WHILE_STATEMENT) {
      if (role == ChildRole.LOOP_BODY) {
        return Wrap.createWrap(WrapType.NORMAL, true);
      } else if (role == ChildRole.WHILE_KEYWORD) {
        return Wrap.createWrap(WrapType.NORMAL, true);
      }
    }

    return defaultWrap;
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
    }
    else {
      final PsiBinaryExpression expr1 = (PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(myNode);
      final PsiBinaryExpression expr2 = (PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(node);
      final PsiJavaToken op1 = expr1.getOperationSign();
      final PsiJavaToken op2 = expr2.getOperationSign();
      return op1.getTokenType() == op2.getTokenType();
    }
  }

  protected static WrapType getWrapType(final int wrap) {
    switch (wrap) {
      case CodeStyleSettings.WRAP_ALWAYS:
        return WrapType.ALWAYS;
      case CodeStyleSettings.WRAP_AS_NEEDED:
        return WrapType.NORMAL;
      case CodeStyleSettings.DO_NOT_WRAP:
        return WrapType.NONE;
      default:
        return WrapType.CHOP_DOWN_IF_LONG;
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
    final Indent externalIndent = Indent.getNoneIndent();
    final Indent internalIndent = Indent.getContinuationIndent();
    AlignmentStrategy alignmentStrategy = AlignmentStrategy.createDoNotAlingCommaStrategy(createAlignment(doAlign, null));
    setChildIndent(internalIndent);
    setChildAlignment(alignmentStrategy.getAlignment(null));

    boolean isAfterIncomplete = false;

    ASTNode prev = child;
    while (child != null) {
      isAfterIncomplete = isAfterIncomplete || child.getElementType() == ElementType.ERROR_ELEMENT ||
                          child.getElementType() == ElementType.EMPTY_EXPRESSION;
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        if (child.getElementType() == from) {
          result.add(createJavaBlock(child, mySettings, externalIndent, null, null));
        }
        else if (child.getElementType() == to) {
          result.add(createJavaBlock(child, mySettings,
                                     isAfterIncomplete ? internalIndent : externalIndent,
                                     null,
                                     isAfterIncomplete ? alignmentStrategy.getAlignment(null) : null));
          return child;
        }
        else {
          final IElementType elementType = child.getElementType();
          result.add(createJavaBlock(child, mySettings, internalIndent,
                                     wrappingStrategy.getWrap(elementType),
                                     alignmentStrategy.getAlignment(elementType)));
          if (to == null) {//process only one statement
            return child;
          }
        }
        isAfterIncomplete = false;
      }
      prev = child;
      child = child.getTreeNext();
    }

    return prev;
  }

  @Nullable
  private ASTNode processEnumBlock(List<Block> result,
                                   ASTNode child,
                                   ASTNode last) {

    final WrappingStrategy wrappingStrategy = WrappingStrategy.createDoNotWrapCommaStrategy(Wrap
      .createWrap(getWrapType(mySettings.ENUM_CONSTANTS_WRAP), true));
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        result.add(createJavaBlock(child, mySettings, Indent.getNormalIndent(),
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

  private static Alignment createAlignmentOrDefault(final Alignment defaultAlignment) {
    return defaultAlignment == null ? Alignment.createAlignment() : defaultAlignment;
  }

  private int getBraceStyle() {
    final PsiElement psiNode = SourceTreeToPsiMap.treeElementToPsi(myNode);
    if (psiNode instanceof PsiClass) {
      return mySettings.CLASS_BRACE_STYLE;
    }
    else if (psiNode instanceof PsiMethod) {
      return mySettings.METHOD_BRACE_STYLE;
    }

    else if (psiNode instanceof PsiCodeBlock && psiNode.getParent() != null && psiNode.getParent() instanceof PsiMethod) {
      return mySettings.METHOD_BRACE_STYLE;
    }

    else {
      return mySettings.BRACE_STYLE;
    }

  }

  protected Indent getCodeBlockInternalIndent(final int baseChildrenIndent) {
    if (isTopLevelClass() && mySettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS) {
      return Indent.getNoneIndent();
    }

    final int braceStyle = getBraceStyle();
    return braceStyle == CodeStyleSettings.NEXT_LINE_SHIFTED ?
           createNormalIndent(baseChildrenIndent - 1)
           : createNormalIndent(baseChildrenIndent);
  }

  protected static Indent createNormalIndent(final int baseChildrenIndent) {
    if (baseChildrenIndent == 1) {
      return Indent.getNormalIndent();
    }
    else if (baseChildrenIndent <= 0) {
      return Indent.getNoneIndent();
    }
    else {
      LOG.assertTrue(false);
      return Indent.getNormalIndent();
    }
  }

  private boolean isTopLevelClass() {
    if (myNode.getElementType() != ElementType.CLASS) return false;
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getTreeParent()) instanceof PsiFile;
  }

  protected Indent getCodeBlockExternalIndent() {
    final int braceStyle = getBraceStyle();
    if (braceStyle == CodeStyleSettings.END_OF_LINE || braceStyle == CodeStyleSettings.NEXT_LINE ||
        braceStyle == CodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      return Indent.getNoneIndent();
    }
    else {
      return Indent.getNormalIndent();
    }
  }

  protected abstract Wrap getReservedWrap();

  protected abstract void setReservedWrap(final Wrap reservedWrap);

  @Nullable
  protected static ASTNode getTreeNode(final Block child2) {
    if (child2 instanceof JavaBlock) {
      return ((JavaBlock)child2).getFirstTreeNode();
    }
    else if (child2 instanceof LeafBlock) {
      return ((LeafBlock)child2).getTreeNode();
    }
    else {
      return null;
    }
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myUseChildAttributes) {
      return new ChildAttributes(myChildIndent, myChildAlignment);
    }
    else if (isAfter(newChildIndex, new IElementType[]{JavaDocElementType.DOC_COMMENT})) {
      return new ChildAttributes(Indent.getNoneIndent(), myChildAlignment);
    }
    else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  @Nullable
  protected Indent getChildIndent() {
    return getChildIndent(myNode);
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  protected boolean isAfter(final int newChildIndex, final IElementType[] elementTypes) {
    if (newChildIndex == 0) return false;
    final Block previousBlock = getSubBlocks().get(newChildIndex - 1);
    if (!(previousBlock instanceof AbstractBlock)) return false;
    final IElementType previousElementType = ((AbstractBlock)previousBlock).getNode().getElementType();
    for (IElementType elementType : elementTypes) {
      if (previousElementType == elementType) return true;
    }
    return false;
  }

  protected Alignment getUsedAlignment(final int newChildIndex) {
    final List<Block> subBlocks = getSubBlocks();
    for (int i = 0; i < newChildIndex; i++) {
      if (i >= subBlocks.size()) return null;
      final Block block = subBlocks.get(i);
      final Alignment alignment = block.getAlignment();
      if (alignment != null) return alignment;
    }
    return null;
  }
}
