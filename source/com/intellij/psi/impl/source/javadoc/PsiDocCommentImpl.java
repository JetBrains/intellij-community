package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.JavaDocLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.text.CharArrayCharSequence;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class PsiDocCommentImpl extends CompositePsiElement implements PsiDocComment, JavaTokenType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.javadoc.PsiDocCommentImpl");

  private static final TokenSet TAG_BIT_SET = TokenSet.create(new IElementType[]{DOC_TAG});
  private static final PsiElementArrayConstructor<PsiDocTag> PSI_TAG_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiDocTag>() {
    public PsiDocTag[] newPsiElementArray(int length) {
      return length != 0 ? new PsiDocTag[length] : PsiDocTag.EMPTY_ARRAY;
    }
  };

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final Pattern WS_PATTERN = Pattern.compile("\\s*");


  public PsiDocCommentImpl() {
    super(JavaDocElementType.DOC_COMMENT);
  }

  public PsiElement[] getDescriptionElements() {
    ChameleonTransforming.transformChildren(this);
    ArrayList<ASTNode> array = new ArrayList<ASTNode>();
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      IElementType i = child.getElementType();
      if (i == DOC_TAG) break;
      if (i != DOC_COMMENT_START && i != DOC_COMMENT_END && i != DOC_COMMENT_LEADING_ASTERISKS) {
        array.add(child);
      }
    }
    return array.toArray(new PsiElement[array.size()]);
  }

  public PsiDocTag[] getTags() {
    return getChildrenAsPsiElements(TAG_BIT_SET, PSI_TAG_ARRAY_CONSTRUCTOR);
  }

  public PsiDocTag findTagByName(String name) {
    if (getFirstChildNode().getElementType() == JavaDocElementType.DOC_COMMENT) {
      if (getFirstChildNode().getText().indexOf(name) < 0) return null;
    }

    char[] tagChars = new char[name.length() + 1];
    tagChars[0] = '@';
    name.getChars(0, name.length(), tagChars, 1);

    ChameleonTransforming.transformChildren(this);
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == DOC_TAG) {
        PsiDocTag tag = (PsiDocTag)SourceTreeToPsiMap.treeElementToPsi(child);
        if (tag.getNameElement().textMatches(new CharArrayCharSequence(tagChars))) {
          return tag;
        }
      }
    }

    return null;
  }

  public PsiDocTag[] findTagsByName(String name) {
    ArrayList<PsiDocTag> array = new ArrayList<PsiDocTag>();
    PsiDocTag[] tags = getTags();
    name = "@" + name;
    for (PsiDocTag tag : tags) {
      if (tag.getNameElement().getText().equals(name)) {
        array.add(tag);
      }
    }
    return array.toArray(new PsiDocTag[array.size()]);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    ChameleonTransforming.transformChildren(this);
    switch (role) {
      default:
        return null;

      case ChildRole.DOC_COMMENT_START:
        return getFirstChildNode();

      case ChildRole.DOC_COMMENT_END:
        if (getLastChildNode().getElementType() == DOC_COMMENT_END) {
          return getLastChildNode();
        }
        else {
          return null;
        }
    }
  }

  private static boolean isWhitespaceCommentData(ASTNode docCommentData) {
    return WS_PATTERN.matcher(docCommentData.getText()).matches();
  }

  private static void addNewLineToTag(CompositeElement tag, Project project) {
    LOG.assertTrue(tag != null && tag.getElementType() == DOC_TAG);
    ASTNode current = tag.getLastChildNode();
    while (current != null && current.getElementType() == DOC_COMMENT_DATA && isWhitespaceCommentData(current)) {
      current = current.getTreePrev();
    }
    if (current != null && current.getElementType() == DOC_COMMENT_LEADING_ASTERISKS) return;
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(tag);
    final ASTNode newLine = Factory.createSingleLeafElement(DOC_COMMENT_DATA, new char[]{'\n'}, 0, 1, treeCharTab, SharedImplUtil.getManagerByTree(tag));
    tag.addChild(newLine, null);

    if (CodeStyleSettingsManager.getSettings(project).JD_LEADING_ASTERISKS_ARE_ENABLED) {
      final TreeElement leadingAsterisk = Factory.createSingleLeafElement(DOC_COMMENT_LEADING_ASTERISKS, new char[]{'*'}, 0, 1, treeCharTab,
                                                                          SharedImplUtil.getManagerByTree(tag));

      tag.addInternal(leadingAsterisk, leadingAsterisk, null, Boolean.TRUE);
    }
    final TreeElement commentData = Factory.createSingleLeafElement(DOC_COMMENT_DATA, new char[]{' '}, 0, 1, treeCharTab, SharedImplUtil.getManagerByTree(tag));
    tag.addInternal(commentData, commentData, null, Boolean.TRUE);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    boolean needToAddNewline = false;
    if (first == last && first.getElementType() == DOC_TAG) {
      if (anchor == null) {
        anchor = getLastChildNode(); // this is a '*/'
        final ASTNode prevBeforeWS = TreeUtil.skipElementsBack(anchor.getTreePrev(), WHITE_SPACE_BIT_SET);
        if (prevBeforeWS != null) {
          anchor = prevBeforeWS;
          before = Boolean.FALSE;
        }
        else {
          before = Boolean.TRUE;
        }
        needToAddNewline = true;
      }
      if (anchor.getElementType() != DOC_TAG) {
        final CharTable charTable = SharedImplUtil.findCharTableByTree(this);
        final TreeElement newLine = Factory.createSingleLeafElement(DOC_COMMENT_DATA, new char[]{'\n'}, 0, 1, charTable, getManager());
        final TreeElement leadingAsterisk = Factory.createSingleLeafElement(DOC_COMMENT_LEADING_ASTERISKS, new char[]{'*'}, 0, 1, charTable, getManager());
        final TreeElement commentData = Factory.createSingleLeafElement(DOC_COMMENT_DATA, new char[]{' '}, 0, 1, charTable, getManager());
        newLine.getTreeParent().addChild(leadingAsterisk);
        newLine.getTreeParent().addChild(commentData);
        super.addInternal(newLine, commentData, anchor, Boolean.FALSE);

        anchor = commentData;
        before = Boolean.FALSE;
      }
      else {
        needToAddNewline = true;
      }
    }
    if(before) anchor.getTreeParent().addChildren(first, last.getTreeNext(), anchor);
    else anchor.getTreeParent().addChildren(first, last.getTreeNext(), anchor.getTreeNext());

    if (needToAddNewline) {
      if (first.getTreePrev() != null && first.getTreePrev().getElementType() == DOC_TAG) {
        addNewLineToTag((CompositeElement)first.getTreePrev(), getProject());
      }
      if (first.getTreeNext() != null && first.getTreeNext().getElementType() == DOC_TAG) {
        addNewLineToTag((CompositeElement)first, getProject());
      }
      else {
        removeEndingAsterisksFromTag((CompositeElement)first);
      }
    }
    return first;
  }

  private static void removeEndingAsterisksFromTag(CompositeElement tag) {
    ASTNode current = tag.getLastChildNode();
    while (current != null && current.getElementType() == DOC_COMMENT_DATA) {
      current = current.getTreePrev();
    }
    if (current != null && current.getElementType() == DOC_COMMENT_LEADING_ASTERISKS) {
      final ASTNode prevWhiteSpace = TreeUtil.skipElementsBack(current.getTreePrev(), WHITE_SPACE_BIT_SET);
      ASTNode toBeDeleted = prevWhiteSpace.getTreeNext();
      while (toBeDeleted != null) {
        ASTNode next = toBeDeleted.getTreeNext();
        tag.deleteChildInternal(toBeDeleted);
        toBeDeleted = next;
      }
    }
  }

  public void deleteChildInternal(ASTNode child) {
    if (child.getElementType() == DOC_TAG) {
      if (child.getTreeNext() == null || child.getTreeNext().getElementType() != DOC_TAG) {
        ASTNode prev = child.getTreePrev();
        while (prev != null && prev.getElementType() == DOC_COMMENT_DATA) {
          prev = prev.getTreePrev();
        }
        ASTNode next = child.getTreeNext();
        while (next != null && (next.getElementType() == DOC_COMMENT_DATA || next.getElementType() == WHITE_SPACE)) {
          next = next.getTreeNext();
        }

        if (prev != null && prev.getElementType() == DOC_COMMENT_LEADING_ASTERISKS && !(next instanceof PsiDocTag)) {
          ASTNode leadingAsterisk = prev;
          if (leadingAsterisk.getTreePrev() != null) {
            super.deleteChildInternal(leadingAsterisk.getTreePrev());
            super.deleteChildInternal(leadingAsterisk);
          }
        }
        else if (prev != null && prev.getElementType() == DOC_TAG) {
          final CompositeElement compositePrev = (CompositeElement)prev;
          final ASTNode lastPrevChild = compositePrev.getLastChildNode();
          ASTNode prevChild = lastPrevChild;
          while (prevChild != null && prevChild.getElementType() == DOC_COMMENT_DATA) {
            prevChild = prevChild.getTreePrev();
          }
          if (prevChild != null && prevChild.getElementType() == DOC_COMMENT_LEADING_ASTERISKS) {
            ASTNode current = prevChild;
            while (current != null) {
              final ASTNode nextChild = current.getTreeNext();
              compositePrev.deleteChildInternal(current);
              current = nextChild;
            }
          }
        }
      }

    }
    super.deleteChildInternal(child);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == DOC_TAG) {
      return ChildRole.DOC_TAG;
    }
    else if (i == JavaDocElementType.DOC_COMMENT || i == DOC_INLINE_TAG) {
      return ChildRole.DOC_CONTENT;
    }
    else if (i == DOC_COMMENT_LEADING_ASTERISKS) {
      return ChildRole.DOC_COMMENT_ASTERISKS;
    }
    else if (i == DOC_COMMENT_START) {
      return ChildRole.DOC_COMMENT_START;
    }
    else if (i == DOC_COMMENT_END) {
      return ChildRole.DOC_COMMENT_END;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocComment(this);
  }

  public String toString() {
    return "PsiDocComment";
  }

  public Class getLexerClass() {
    return JavaDocLexer.class;
  }
}
