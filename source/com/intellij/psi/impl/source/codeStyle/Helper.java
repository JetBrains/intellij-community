package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.CharTable;

public class Helper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.Helper");

  private final CodeStyleSettings mySettings;
  private final FileType myFileType;
  private final Project myProject;

  public Helper(FileType fileType, Project project) {
    mySettings = CodeStyleSettingsManager.getSettings(project);
    myProject = project;
    myFileType = fileType;
  }

  private static int getStartOffset(ASTNode root, ASTNode child) {
    if (child == root) return 0;
    ASTNode parent = child.getTreeParent();
    int offset = 0;
    for (ASTNode child1 = parent.getFirstChildNode(); child1 != child; child1 = child1.getTreeNext()) {
      offset += child1.getTextLength();
    }
    return getStartOffset(root, parent) + offset;
  }

  //----------------------------------------------------------------------------------------------------

  public static final int INDENT_FACTOR = 10000; // "indent" is indent_level * INDENT_FACTOR + spaces

  public int getIndent(ASTNode element) {
    return getIndent(element, false);
  }

  public int getIndent(final ASTNode element, boolean includeNonSpace) {
    return getIndent(element, includeNonSpace, 0);
  }
  
  private static final int TOO_BIG_WALK_THRESHOULD = 450;

  private int getIndent(final ASTNode element, boolean includeNonSpace, int recursionLevel) {
    if (recursionLevel > TOO_BIG_WALK_THRESHOULD) return 0;

    if (element.getTreePrev() != null) {
      ASTNode prev = element.getTreePrev();
      ASTNode lastCompositePrev;
      while (prev instanceof CompositeElement && !(prev instanceof XmlText)) {
        lastCompositePrev = prev;
        prev = prev.getLastChildNode();
        if (prev == null) { // element.prev is "empty composite"
          return getIndent(lastCompositePrev, includeNonSpace, recursionLevel + 1);
        }
      }

      String text = prev.getText();
      int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));

      if (index >= 0) {
        return getIndent(text.substring(index + 1), includeNonSpace);
      }

      if (includeNonSpace) {
        return getIndent(prev, includeNonSpace, recursionLevel + 1) + getIndent(text, includeNonSpace);
      }

      if (element.getElementType() == ElementType.CODE_BLOCK) {
        ASTNode parent = element.getTreeParent();
        if (parent.getElementType() == ElementType.BLOCK_STATEMENT) {
          parent = parent.getTreeParent();
        }
        if (parent.getElementType() != ElementType.CODE_BLOCK) {
          //Q: use some "anchor" part of parent for some elements?
          // e.g. for method it could be declaration start, not doc-comment
          return getIndent(parent, includeNonSpace, recursionLevel + 1);
        }
      }
      else {
        if (element.getElementType() == ElementType.LBRACE) {
          return getIndent(element.getTreeParent(), includeNonSpace, recursionLevel + 1);
        }
      }
      //Q: any other cases?

      ASTNode parent = prev.getTreeParent();
      ASTNode child = prev;
      while (parent != null) {
        if (child.getTreePrev() != null) break;
        child = parent;
        parent = parent.getTreeParent();
      }
      if (parent == null) {
        return getIndent(text, includeNonSpace);
      }
      else {
        if (prev.getTreeParent().getElementType() == ElementType.LABELED_STATEMENT) {
          return getIndent(prev, true, recursionLevel + 1) + getIndent(text, true);
        }
        else
          return getIndent(prev, includeNonSpace, recursionLevel + 1);
      }
    }
    else {
      if (element.getTreeParent() == null) {
        return 0;
      }
      return getIndent(element.getTreeParent(), includeNonSpace, recursionLevel + 1);
    }
  }

  public String fillIndent(int indent) {
    int indentLevel = (indent + INDENT_FACTOR / 2) / INDENT_FACTOR;
    int spaceCount = indent - indentLevel * INDENT_FACTOR;
    int indentLevelSize = indentLevel * mySettings.getIndentSize(getFileType());
    int totalSize = indentLevelSize + spaceCount;

    StringBuffer buffer = new StringBuffer();
    if (mySettings.useTabCharacter(getFileType())) {
      if (mySettings.isSmartTabs(getFileType())) {
        int tabCount = indentLevelSize / mySettings.getTabSize(getFileType());
        int leftSpaces = indentLevelSize - tabCount * mySettings.getTabSize(getFileType());
        for (int i = 0; i < tabCount; i++) {
          buffer.append('\t');
        }
        for (int i = 0; i < leftSpaces + spaceCount; i++) {
          buffer.append(' ');
        }
      }
      else {
        int size = totalSize;
        while (size > 0) {
          if (size >= mySettings.getTabSize(getFileType())) {
            buffer.append('\t');
            size -= mySettings.getTabSize(getFileType());
          }
          else {
            buffer.append(' ');
            size--;
          }
        }
      }
    }
    else {
      for (int i = 0; i < totalSize; i++) {
        buffer.append(' ');
      }
    }

    return buffer.toString();
  }

  public int getIndent(String text, boolean includeNonSpace) {
    int i;
    for (i = text.length() - 1; i >= 0; i--) {
      char c = text.charAt(i);
      if (c == '\n' || c == '\r') break;
    }
    i++;

    int spaceCount = 0;
    int tabCount = 0;
    for (int j = i; j < text.length(); j++) {
      char c = text.charAt(j);
      if (c != '\t') {
        if (!includeNonSpace && c != ' ') break;
        spaceCount++;
      }
      else {
        tabCount++;
      }
    }

    if (tabCount == 0) return spaceCount;

    int tabSize = mySettings.getTabSize(getFileType());
    int indentLevel = tabCount * tabSize / mySettings.getIndentSize(getFileType());
    return indentLevel * INDENT_FACTOR + spaceCount;
  }

  public ASTNode shiftIndentInside(ASTNode element, int indentShift) {
    if (indentShift == 0) return element;
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(element);
    String text = element.getText();
    for (int offset = 0; offset < text.length(); offset++) {
      char c = text.charAt(offset);
      if (c == '\n' || c == '\r') {
        int offset1;
        for (offset1 = offset + 1; offset1 < text.length(); offset1++) {
          c = text.charAt(offset1);
          if (c != ' ' && c != '\t') break;
        }
                    if (c == '\n' || c == '\r') continue;
        String space = text.substring(offset + 1, offset1);
        int indent = getIndent(space, true);
        int newIndent = indent + indentShift;
        newIndent = Math.max(newIndent, 0);
        String newSpace = fillIndent(newIndent);

        ASTNode leaf = element.findLeafElementAt(offset);
        if (!mayShiftIndentInside(leaf)) {
          LOG.error("Error",
                    leaf.getElementType().toString(),
                    "Type: " + leaf.getElementType() + " text: " + leaf.getText()
                    );
        }

        if (offset1 < text.length()) {
          ASTNode next = element.findLeafElementAt(offset1);
          if ((next.getElementType() == ElementType.END_OF_LINE_COMMENT
               || next.getElementType() == ElementType.C_STYLE_COMMENT
               || next.getElementType() == JspElementType.JSP_COMMENT
          ) &&
              next != element) {
            if (mySettings.KEEP_FIRST_COLUMN_COMMENT) {
              int commentIndent = getIndent(next, true);
              if (commentIndent == 0) continue;
            }
          }
          else if (next.getElementType() == XmlElementType.XML_DATA_CHARACTERS) {
            continue;
          }
        }

        int leafOffset = getStartOffset(element, leaf);
        if (leaf.getElementType() == ElementType.DOC_COMMENT_DATA && leafOffset + leaf.getTextLength() == offset + 1) {
          ASTNode next = element.findLeafElementAt(offset + 1);
          if (next.getElementType() == ElementType.WHITE_SPACE) {
            leaf = next;
            leafOffset = getStartOffset(element, leaf);
          }
          else {
            if (newSpace.length() > 0) {
              LeafElement newLeaf = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, newSpace, 0,
                                                                    newSpace.length(), charTableByTree, SharedImplUtil.getManagerByTree(next));
              next.getTreeParent().addChild(newLeaf, next);
            }
            text = text.substring(0, offset + 1) + newSpace + text.substring(offset1);
            continue;
          }
        }

        int startOffset = offset + 1 - leafOffset;
        int endOffset = offset1 - leafOffset;
        if (!LOG.assertTrue(0 <= startOffset && startOffset <= endOffset && endOffset <= leaf.getTextLength())) {
          continue;
        }
        String leafText = leaf.getText();
        String newLeafText = leafText.substring(0, startOffset) + newSpace + leafText.substring(endOffset);
        if (newLeafText.length() > 0) {
          LeafElement newLeaf = Factory.createSingleLeafElement(leaf.getElementType(), newLeafText, 0,
                                                                newLeafText.length(), charTableByTree, SharedImplUtil.getManagerByTree(leaf));
          if (leaf.getTreeParent() != null) {
            leaf.getTreeParent().replaceChild(leaf, newLeaf);
          }
          if (leaf == element) {
            element = newLeaf;
          }
        }
        else {
          ASTNode parent = leaf.getTreeParent();
          if (parent != null) {
            parent.removeChild(leaf);
          }
        }
        text = text.substring(0, offset + 1) + newSpace + text.substring(offset1);
      }
    }
    return element;
  }

  public static boolean mayShiftIndentInside(final ASTNode leaf) {
    return (isComment(leaf) && !checkJspTexts(leaf))
           || leaf.getElementType() == ElementType.WHITE_SPACE
           || leaf.getElementType() == XmlElementType.XML_DATA_CHARACTERS
           || leaf.getElementType() == JspElementType.JAVA_CODE
           || leaf.getElementType() == JspElementType.JSP_SCRIPTLET
           || leaf.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN;
  }

  private static boolean checkJspTexts(final ASTNode leaf) {
    ASTNode child = leaf.getFirstChildNode();
    while(child != null){
      if(child instanceof OuterLanguageElement) return true;
      child = child.getTreeNext();
    }
    return false;
  }

  private static  boolean isComment(final ASTNode node) {
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(node);
    if (psiElement instanceof PsiComment) return true;
    final ParserDefinition parserDefinition = psiElement.getLanguage().getParserDefinition();
    if (parserDefinition == null) return false;
    final TokenSet commentTokens = parserDefinition.getCommentTokens();
    return commentTokens.contains(node.getElementType());
  }

  public FileType getFileType() {
    return myFileType;
  }

  public Project getProject() {
    return myProject;
  }

}
