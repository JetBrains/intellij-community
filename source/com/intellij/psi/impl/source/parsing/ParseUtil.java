package com.intellij.psi.impl.source.parsing;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.JavaWithJspTemplateDataLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.ModifierListElement;
import com.intellij.psi.jsp.AbstractJspJavaLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;

/**
 *
 */
public class ParseUtil implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ParseUtil");

  public static TreeElement createTokenElement(Lexer lexer, CharTable table) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) return null;
    if (tokenType == JavaTokenType.DOC_COMMENT) {
      return ASTFactory.leaf(JavaDocElementType.DOC_COMMENT, lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(), table);
    }
    else {
      return ASTFactory.leaf(tokenType, lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(), table);
    }
  }

  /*public static void insertMissingTokens(CompositeElement root,
                                         Lexer lexer,
                                         int startOffset,
                                         int endOffset,
                                         final int state, TokenProcessor processor, ParsingContext context) {
    insertMissingTokens(root, lexer, startOffset, endOffset, -1, processor, context);
  }*/

  public static void insertMissingTokens(CompositeElement root,
                                         Lexer lexer,
                                         int startOffset,
                                         int endOffset,
                                         int state,
                                         TokenProcessor processor,
                                         ParsingContext context) {
    final MissingTokenInserter inserter;
    if (lexer instanceof JavaLexer || lexer instanceof JavaWithJspTemplateDataLexer || lexer instanceof AbstractJspJavaLexer) {
      inserter = new JavaMissingTokenInserter(root, lexer, startOffset, endOffset, state, processor, context);
    }
    else {
      inserter = new MissingTokenInserter(root, lexer, startOffset, endOffset, state, processor, context);
    }
    inserter.invoke();
  }


  private static class JavaMissingTokenInserter extends MissingTokenInserter {

    public JavaMissingTokenInserter(final CompositeElement root, final Lexer lexer, final int startOffset, final int endOffset, final int state,
                                    final TokenProcessor processor,
                                    final ParsingContext context) {
      super(root, lexer, startOffset, endOffset, state, processor, context);
    }

    @Override
    public void invoke() {
      super.invoke();
      bindComments(myRoot);
    }

    @Override
    protected IElementType getNextTokenType() {
      return GTTokens.getTokenType(myLexer);
    }

    @Override
    protected void advanceLexer(final ASTNode next) {
      GTTokens.advance(next.getElementType(), myLexer);
    }

    private static void bindComments(ASTNode root) {
      TreeElement child = (TreeElement)root.getFirstChildNode();
      while (child != null) {
        if (child.getElementType() == JavaDocElementType.DOC_COMMENT) {
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

        if (child instanceof CompositeElement) {
          bindComments(child);
        }
        child = child.getTreeNext();
      }

      //pass 2: bind preceding comments (like "// comment \n void f();")
      child = (TreeElement)root.getFirstChildNode();
      while(child != null) {
        if (child.getElementType() == END_OF_LINE_COMMENT || child.getElementType() == C_STYLE_COMMENT) {
          TreeElement next = (TreeElement)TreeUtil.skipElements(child, PRECEDING_COMMENT_OR_SPACE_BIT_SET);
          bindPrecedingComment(child, next);
          child = next;
        } else {
          child = child.getTreeNext();
        }
      }
    }

    private static boolean bindDocComment(TreeElement docComment) {
      TreeElement element = docComment.getTreeNext();
      if (element == null) return false;
      TreeElement startSpaces = null;

      TreeElement importList = null;
      // Bypass meaningless tokens and hold'em in hands
      while (element.getElementType() == WHITE_SPACE || element.getElementType() == C_STYLE_COMMENT ||
             element.getElementType() == END_OF_LINE_COMMENT || (element.getElementType() == IMPORT_LIST && element.getTextLength() == 0)) {
        if (element.getElementType() == IMPORT_LIST) importList = element;
        if (startSpaces == null) startSpaces = element;
        element = element.getTreeNext();
        if (element == null) return false;
      }

      if (element.getElementType() == CLASS || element.getElementType() == FIELD || element.getElementType() == METHOD ||
          element.getElementType() == ENUM_CONSTANT || element.getElementType() == ANNOTATION_METHOD) {
        TreeElement first = element.getFirstChildNode();
        if (startSpaces != null) {
          TreeUtil.removeRange(docComment, element);
        }
        else {
          TreeUtil.remove(docComment);
        }

        TreeUtil.insertBefore(first, docComment);

        if (importList != null) {
          TreeUtil.remove(importList);
          TreeUtil.insertBefore(element, importList);
        }

        return true;
      }
      return false;
    }

    private static final TokenSet BIND_TRAILING_COMMENT_BIT_SET = TokenSet.orSet(
      TokenSet.create(FIELD, METHOD, CLASS, CLASS_INITIALIZER, IMPORT_STATEMENT, IMPORT_STATIC_STATEMENT, PACKAGE_STATEMENT),
      JAVA_STATEMENT_BIT_SET);

    private static boolean bindTrailingComment(TreeElement comment) {
      TreeElement element = comment.getTreePrev();
      if (element == null) return false;
      TreeElement space = null;
      if (element.getElementType() == WHITE_SPACE) {
        space = element;
        element = element.getTreePrev();
      }
      if (element != null && BIND_TRAILING_COMMENT_BIT_SET.contains(element.getElementType())) {
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

    private static final TokenSet BIND_PRECEDING_COMMENT_BIT_SET = TokenSet.create(FIELD, METHOD, CLASS, CLASS_INITIALIZER);

    private static final TokenSet PRECEDING_COMMENT_OR_SPACE_BIT_SET = TokenSet.create(C_STYLE_COMMENT, END_OF_LINE_COMMENT, WHITE_SPACE);

    private static void bindPrecedingComment(TreeElement comment, ASTNode bindTo) {
      if (bindTo == null || bindTo.getFirstChildNode() != null &&
                             bindTo.getFirstChildNode().getElementType() == JavaTokenType.DOC_COMMENT) return;

      if (bindTo.getElementType() == IMPORT_LIST && bindTo.getTextLength() == 0) {
        bindTo = bindTo.getTreeNext();
      }

      ASTNode toStart = isBindingComment(comment) ? comment : null;
      if (bindTo != null && BIND_PRECEDING_COMMENT_BIT_SET.contains(bindTo.getElementType())) {
        for (ASTNode child = comment; child != bindTo; child = child.getTreeNext()) {
          if (child.getElementType() == WHITE_SPACE) {
            int count = StringUtil.getLineBreakCount(child.getText());
            if (count > 1) toStart = null;
          }
          else {
            if (child.getTreePrev() != null && child.getTreePrev().getElementType() == WHITE_SPACE) {
              LeafElement prev = (LeafElement)child.getTreePrev();
              char lastC = prev.charAt(prev.getTextLength() - 1);
              if (lastC == '\n' || lastC == '\r') toStart = isBindingComment(child) ? child : null;
            }
            else {
              return;
            }
          }
        }

        if (toStart == null) return;

        TreeElement first = (TreeElement)bindTo.getFirstChildNode();
        TreeElement child = (TreeElement)toStart;
        while (child != bindTo) {
          TreeElement next = child.getTreeNext();
          if (child.getElementType() != IMPORT_LIST) {
            TreeUtil.remove(child);
            TreeUtil.insertBefore(first, child);
          }
          child = next;
        }
      }
    }

    private static boolean isBindingComment(final ASTNode node) {
      ASTNode prev = node.getTreePrev();
      if (prev != null) {
        if (prev.getElementType() != WHITE_SPACE) {
          return false;
        }
        else {
          if (!prev.textContains('\n')) return false;
        }
      }

      return true;
    }

    protected boolean isInsertAfterElement(final TreeElement treeNext) {
      return treeNext instanceof ModifierListElement;
    }
  }
}
