package com.intellij.psi.impl.source.parsing;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.parsing.jsp.JspStep1Lexer;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.ModifierListElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.util.CharTable;

/**
 *
 */
public class ParseUtil implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ParseUtil");

  public static final Key<String> UNCLOSED_ELEMENT_PROPERTY = Key.create("UNCLOSED_ELEMENT_PROPERTY");

  public static boolean isStrongWhitespaceHolder(IElementType type){
    if (type == XmlElementType.XML_TEXT) return true;
    return false;
  }

  public static TreeElement createTokenElement(Lexer lexer, CharTable table) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) return null;
    if (tokenType == DOC_COMMENT) {
      CompositeElement element = Factory.createCompositeElement(tokenType);
      LeafElement chameleon = Factory.createLeafElement(DOC_COMMENT_TEXT, lexer.getBuffer(), lexer.getTokenStart(),
                                                        lexer.getTokenEnd(), lexer.getState(), table);
      TreeUtil.addChildren(element, chameleon);
      return element;
    }
    else {
      final LeafElement leafElement = Factory.createLeafElement(tokenType, lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd(),
                                                                lexer.getState(), table);
      leafElement.setState(lexer.getState());
      return leafElement;
    }
  }

  public static long savePosition(Lexer lexer) {
    return lexer.getTokenStart() | (long)lexer.getState() << 32;
  }

  public static int getStoredPosition(long position) {
    return (int)position & 0xFFFFFFFF;
  }

  public static int getStoredState(long startPos) {
    return (int)(startPos >> 32);
  }

  public static void restorePosition(Lexer lexer, long position) {
    lexer.start(lexer.getBuffer(), (int)position & 0xFFFFFFFF, lexer.getBufferEnd(), (int)(position >> 32));
  }

  public static int addTokens(CompositeElement parent, Lexer lexer, TokenSet typeBitSet, ParsingContext context) {
    int count = 0;
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      if (!typeBitSet.isInSet(tokenType)) break;
      if (parent != null) {
        TreeUtil.addChildren(parent, ParseUtil.createTokenElement(lexer, context.getCharTable()));
      }
      lexer.advance();
      count++;
    }
    return count;
  }

  public static int addTokensUntil(CompositeElement parent, Lexer lexer, IElementType stopType, ParsingContext context) {
    int count = 0;
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      if (tokenType == stopType) break;
      if (parent != null) {
        TreeUtil.addChildren(parent, createTokenElement(lexer, context.getCharTable()));
      }
      lexer.advance();
      count++;
    }
    return count;
  }

  public static String getTokenText(Lexer lexer) {
    return StringFactory.createStringFromConstantArray(lexer.getBuffer(), lexer.getTokenStart(),
                                                       lexer.getTokenEnd() - lexer.getTokenStart());
  }

  public static interface TokenProcessor {
    TreeElement process(Lexer lexer, ParsingContext context);

    boolean isTokenValid(IElementType tokenType);
  }

  public static class WhiteSpaceAndCommentsProcessor implements TokenProcessor {
    public static final TokenProcessor INSTANCE = new WhiteSpaceAndCommentsProcessor();

    private WhiteSpaceAndCommentsProcessor() {
    }

    public TreeElement process(Lexer lexer, ParsingContext context) {
      TreeElement first = null;
      TreeElement last = null;
      while (isTokenValid(lexer.getTokenType())) {
        TreeElement tokenElement = ParseUtil.createTokenElement(lexer, context.getCharTable());
        IElementType type = lexer.getTokenType();
        if (!WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(type)) {
          LOG.error("Missed token should be white space or comment:" + tokenElement);
          throw new RuntimeException();
        }
        if (last != null) {
          last.setTreeNext(tokenElement);
          tokenElement.setTreePrev(last);
          last = tokenElement;
        }
        else {
          first = last = tokenElement;
        }
        lexer.advance();
      }
      return first;
    }

    public boolean isTokenValid(IElementType tokenType) {
      return tokenType != null && WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(tokenType);
    }
  }

  public static final class CommonParentState {
    TreeElement startLeafBranchStart = null;
    TreeElement nextLeafBranchStart = null;
    CompositeElement strongWhiteSpaceHolder = null;
    boolean isStrongElementOnRisingSlope = true;
  }

  public static void insertMissingTokens(CompositeElement root,
                                         Lexer lexer,
                                         int startOffset,
                                         int endOffset,
                                         TokenProcessor processor, ParsingContext context) {
    insertMissingTokens(root, lexer, startOffset, endOffset, -1, processor, context);
  }

  public static void insertMissingTokens(CompositeElement root,
                                         Lexer lexer,
                                         int startOffset,
                                         int endOffset, int state,
                                         TokenProcessor processor, ParsingContext context) {
    if (state < 0) {
      lexer.start(lexer.getBuffer(), startOffset, endOffset);
    }
    else {
      lexer.start(lexer.getBuffer(), startOffset, endOffset, state);
    }

    boolean gt = lexer instanceof JavaLexer || lexer instanceof JspStep1Lexer;
    LeafElement leaf = TreeUtil.findFirstLeaf(root);
    if (leaf == null) {
      final TreeElement firstMissing = processor.process(lexer, context);
      if (firstMissing != null) {
        TreeUtil.addChildren(root, firstMissing);
      }
      return;
    }
    {
      // Missing in the begining
      final IElementType tokenType = gt ? GTTokens.getTokenType(lexer) : lexer.getTokenType();
      if (tokenType != leaf.getElementType() && processor.isTokenValid(tokenType)) {
        final TreeElement firstMissing = processor.process(lexer, context);
        if (firstMissing != null) {
          TreeUtil.insertBefore(root.firstChild, firstMissing);
        }
      }
      passTokenOrChameleon(leaf, lexer, gt);
    }
    // Missing in tree body
    insertMissingTokensInTreeBody(leaf, gt, lexer, processor, context, null);
    if(lexer.getTokenType() != null){
      // whitespaces at the end of the file
      final TreeElement firstMissing = processor.process(lexer, context);
      if(firstMissing != null){
        TreeElement current = root;
        while(current instanceof CompositeElement){
          if(current.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null) break;
          current = ((CompositeElement)current).lastChild;
        }
        if(current instanceof CompositeElement){
          TreeUtil.addChildren((CompositeElement)current, firstMissing);
        }
        else{
          TreeUtil.insertAfter(root.lastChild, firstMissing);
        }
      }
    }
    bindComments(root);
  }

  public static void insertMissingTokensInTreeBody(TreeElement leaf,
                                                   boolean gt,
                                                   Lexer lexer,
                                                   TokenProcessor processor,
                                                   ParsingContext context,
                                                   LeafElement endToken) {
    final CommonParentState commonParents = new CommonParentState();
    while(leaf != null){
      commonParents.strongWhiteSpaceHolder = null;
      final IElementType tokenType = gt ? GTTokens.getTokenType(lexer) : lexer.getTokenType();
      final TreeElement next;
      if(tokenType instanceof IChameleonElementType)
        next = nextLeaf(leaf, commonParents, tokenType);
      else
        next = nextLeaf(leaf, commonParents, null);

      if (next == null || tokenType == null || next == endToken) break;
      if (tokenType != next.getElementType() && processor.isTokenValid(tokenType)) {
        final TreeElement firstMissing = processor.process(lexer, context);
        final CompositeElement unclosedElement = commonParents.strongWhiteSpaceHolder;
        if (unclosedElement != null) {
          if(commonParents.isStrongElementOnRisingSlope || unclosedElement.firstChild == null)
            TreeUtil.addChildren(unclosedElement, firstMissing);
          else
            TreeUtil.insertBefore(unclosedElement.firstChild, firstMissing);
        }
        else {
          final TreeElement insertBefore = commonParents.nextLeafBranchStart;
          TreeElement insertAfter = commonParents.startLeafBranchStart;
          TreeElement current = commonParents.startLeafBranchStart;
          while (current != insertBefore) {
            final TreeElement treeNext = current.getTreeNext();
            if (treeNext == insertBefore) {
              insertAfter = current;
              break;
            }
            if (treeNext instanceof ModifierListElement) {
              insertAfter = current;
              break;
            }
            if (treeNext.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null) {
              insertAfter = null;
              TreeUtil.addChildren((CompositeElement)treeNext, firstMissing);
              break;
            }
            current = treeNext;
          }
          if (insertAfter != null) TreeUtil.insertAfter(insertAfter, firstMissing);
        }
      }
      passTokenOrChameleon(next, lexer, gt);
      leaf = next;
    }
  }

  private static void passTokenOrChameleon(final TreeElement next, Lexer lexer, boolean gtUse) {
    if (next instanceof ChameleonElement) {
      final int endOfChameleon = next.getTextLength() + lexer.getTokenStart();
      while (lexer.getTokenType() != null && lexer.getTokenEnd() < endOfChameleon) {
        lexer.advance();
      }
    }
    if (gtUse) {
      GTTokens.advance(next.getElementType(), lexer);
    }
    else {
      lexer.advance();
    }
  }

  public static LeafElement nextLeaf(TreeElement start, CommonParentState commonParent) {
    return (LeafElement)nextLeaf(start, commonParent, null);
  }

  public static TreeElement nextLeaf(TreeElement start, CommonParentState commonParent, IElementType searchedType) {
    TreeElement next = null;
    if(commonParent != null){
      commonParent.startLeafBranchStart = start;
      initStrongWhitespaceHolder(commonParent, start, true);
    }
    TreeElement nextTree = start;
    while (next == null && (nextTree = nextTree.getTreeNext()) != null) {
      if(nextTree.getElementType() == searchedType)
        return nextTree;
      next = findFirstLeaf(nextTree, searchedType, commonParent);
    }
    if(next != null){
      if(commonParent != null) commonParent.nextLeafBranchStart = nextTree;
      return next;
    }
    final CompositeElement parent = start.getTreeParent();
    if (parent == null) return null;
    return nextLeaf(parent, commonParent, searchedType);
  }

  private static void initStrongWhitespaceHolder(CommonParentState commonParent, TreeElement start, boolean slopeSide) {
    if(start instanceof CompositeElement
       && (isStrongWhitespaceHolder(start.getElementType())
       || (start.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null) && slopeSide)){
      commonParent.strongWhiteSpaceHolder = (CompositeElement)start;
      commonParent.isStrongElementOnRisingSlope = slopeSide;
    }
  }

  private static TreeElement findFirstLeaf(TreeElement element, IElementType searchedType, CommonParentState commonParent) {
    if(commonParent != null){
      initStrongWhitespaceHolder(commonParent, element, false);
    }
    if (element instanceof LeafElement || element.getElementType() == searchedType){
      return element;
    }
    else{
      for(TreeElement child = ((CompositeElement)element).firstChild; child != null; child = child.getTreeNext()){
        TreeElement leaf = findFirstLeaf(child, searchedType, commonParent);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  public static LeafElement prevLeaf(TreeElement start, CommonParentState commonParent) {
    LeafElement prev = null;
    if(commonParent != null){
      if(commonParent.strongWhiteSpaceHolder != null && start.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null)
        commonParent.strongWhiteSpaceHolder = (CompositeElement)start;
      commonParent.startLeafBranchStart = start;
    }
    TreeElement prevTree = start;
    while (prev == null && (prevTree = prevTree.getTreePrev()) != null) {
      prev = TreeUtil.findLastLeaf(prevTree);
    }
    if(prev != null){
      if(commonParent != null) commonParent.nextLeafBranchStart = prevTree;
      return prev;
    }
    final CompositeElement parent = start.getTreeParent();
    if (parent == null) return null;
    return prevLeaf(parent, commonParent);
  }

  static void bindComments(CompositeElement root) {
    TreeElement child = root.firstChild;
    while (child != null) {
      if (child.getElementType() == DOC_COMMENT) {
        if (bindDocComment(child)) {
          child = child.getTreeParent();
          continue;
        }
      }

      // bind "trailing comments" (like "int a; // comment")
      if (child.getElementType() == END_OF_LINE_COMMENT || child.getElementType() == C_STYLE_COMMENT) {
        if (bindTrailingComment(child)) {
          child = child.getTreeParent();
          continue;
        }
      }

      // bind "preceding comments" (like "// comment \n void f();")
      if (child.getElementType() == END_OF_LINE_COMMENT || child.getElementType() == C_STYLE_COMMENT) {
        if (bindPrecedingComment(child)) {
          child = child.getTreeParent();
          if (child.getTreePrev() != null) {
            child = child.getTreePrev();
          }
          continue;
        }
      }

      if (child instanceof CompositeElement) {
        bindComments((CompositeElement)child);
      }
      child = child.getTreeNext();
    }
  }

  private static boolean bindDocComment(TreeElement docComment) {
    TreeElement element = docComment.getTreeNext();
    if (element == null) return false;
    TreeElement startSpaces = null;
    TreeElement endSpaces = null;

    // Bypass meaningless tokens and hold'em in hands
    while (element.getElementType() == WHITE_SPACE ||
           element.getElementType() == C_STYLE_COMMENT ||
           element.getElementType() == END_OF_LINE_COMMENT ||
           (element.getElementType() == IMPORT_LIST && element.getTextLength() == 0)
      ) {
      if (startSpaces == null) startSpaces = element;
      element = element.getTreeNext();
      if (element == null) return false;
    }

    endSpaces = element;

    if (element.getElementType() == CLASS || element.getElementType() == FIELD || element.getElementType() == METHOD ||
        element.getElementType() == ENUM_CONSTANT) {
      TreeElement first = ((CompositeElement)element).firstChild;
      TreeUtil.remove(docComment);
      TreeUtil.insertBefore(first, docComment);
      if (startSpaces != null) {
        element = startSpaces.getTreeNext();

        if (startSpaces.getElementType() != IMPORT_LIST) {
          TreeUtil.remove(startSpaces);
          TreeUtil.insertBefore(first, startSpaces);
        }

        TreeElement anchor = startSpaces;

        while (element != endSpaces) {
          TreeElement next = element.getTreeNext();
          if (element.getElementType() != IMPORT_LIST) {
            TreeUtil.remove(element);
            TreeUtil.insertAfter(anchor, element);
            anchor = element;
          }
          element = next;
        }
      }
      return true;
    }
    return false;
  }

  private static final TokenSet BIND_TRAINLING_COMMENT_BIT_SET = TokenSet.orSet(TokenSet.create(new IElementType[]{
    FIELD,
    METHOD,
    CLASS,
    CLASS_INITIALIZER,
    IMPORT_STATEMENT,
    IMPORT_STATIC_STATEMENT,
    PACKAGE_STATEMENT
  }),
                                                                                     STATEMENT_BIT_SET);

  private static boolean bindTrailingComment(TreeElement comment) {
    TreeElement element = comment.getTreePrev();
    if (element == null) return false;
    TreeElement space = null;
    if (element.getElementType() == WHITE_SPACE) {
      space = element;
      element = element.getTreePrev();
    }
    if (element != null && BIND_TRAINLING_COMMENT_BIT_SET.isInSet(element.getElementType())) {
      if (space == null || (!space.textContains('\n') && !space.textContains('\r'))) {
        if (!comment.textContains('\n') && !comment.textContains('\r')) {
          if (space != null) {
            TreeUtil.remove(space);
            TreeUtil.addChildren((CompositeElement)element, space);
          }
          TreeUtil.remove(comment);
          TreeUtil.addChildren((CompositeElement)element, comment);
          return true;
        }
      }
    }
    return false;
  }

  private static final TokenSet BIND_PRECEDING_COMMENT_BIT_SET = TokenSet.create(new IElementType[]{
    FIELD,
    METHOD,
    CLASS,
    CLASS_INITIALIZER,
  });

  private static final TokenSet PRECEDING_COMMENT_OR_SPACE_BIT_SET = TokenSet.create(new IElementType[]{
    C_STYLE_COMMENT, END_OF_LINE_COMMENT, WHITE_SPACE
  });

  private static boolean bindPrecedingComment(TreeElement comment) {
    TreeElement element = TreeUtil.skipElements(comment, PRECEDING_COMMENT_OR_SPACE_BIT_SET);
    if (element == null) return false;

    if (element.getElementType() == IMPORT_LIST && element.getTextLength() == 0) {
      element = element.getTreeNext();
    }

    if (element != null && BIND_PRECEDING_COMMENT_BIT_SET.isInSet(element.getElementType())) {
      for (TreeElement child = comment; child != element; child = child.getTreeNext()) {
        if (child.getElementType() == WHITE_SPACE) {
          int count = StringUtil.getLineBreakCount(child.getText());
          if (count > 1) return false;
        }
        else {
          if (comment.getTreePrev() != null && comment.getTreePrev().getElementType() == ElementType.WHITE_SPACE) {
            LeafElement prev = (LeafElement)comment.getTreePrev();
            char lastC = prev.charAt(prev.getTextLength() - 1);
            if (lastC == '\n' || lastC == '\r') return false;
          }
          else {
            return false;
          }
        }
      }

      // check if the comment is on separate line
      if (comment.getTreePrev() != null) {
        TreeElement prev = comment.getTreePrev();
        if (prev.getElementType() != ElementType.WHITE_SPACE) {
          return false;
        }
        else {
          if (!prev.textContains('\n')) return false;
        }
      }

      TreeElement first = ((CompositeElement)element).firstChild;
      TreeElement child = comment;
      while (child != element) {
        TreeElement next = child.getTreeNext();
        if (child.getElementType() != IMPORT_LIST) {
          TreeUtil.remove(child);
          TreeUtil.insertBefore(first, child);
        }
        child = next;
      }
      return true;
    }
    return false;
  }
}
