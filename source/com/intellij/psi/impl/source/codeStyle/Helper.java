package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.CharTable;
import java.util.HashMap;
import java.util.Map;

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

  public static boolean isNonSpace(TreeElement element) {
    IElementType elementType = element.getElementType();
    if(elementType == JavaTokenType.WHITE_SPACE) return false;
    else if(elementType == XmlElementType.XML_TEXT && element.getText().trim().length() == 0) return false;
    return element.getTextLength() > 0;
  }

  public static TreeElement shiftForwardToNonSpace(TreeElement element) {
    while (element != null && !isNonSpace(element)) {
      element = element.getTreeNext();
    }
    return element;
  }

  public static TreeElement shiftBackwardToNonSpace(TreeElement element) {
    while (element != null && !isNonSpace(element)) {
      element = element.getTreePrev();
    }
    return element;
  }

  private int getStartOffset(TreeElement root, TreeElement child) {
    if (child == root) return 0;
    CompositeElement parent = child.getTreeParent();
    int offset = 0;
    for (TreeElement child1 = parent.firstChild; child1 != child; child1 = child1.getTreeNext()) {
      offset += child1.getTextLength();
    }
    return getStartOffset(root, parent) + offset;
  }

  public static TreeElement splitSpaceElement(TreeElement space, int offset, CharTable charTable) {
    LOG.assertTrue(space.getElementType() == ElementType.WHITE_SPACE);
    char[] chars = space.textToCharArray();
    LeafElement space1 = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, chars, 0, offset, charTable, null);
    LeafElement space2 = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, chars, offset, chars.length, charTable, null);
    CompositeElement parent = space.getTreeParent();
    ChangeUtil.replaceChild(parent, space, space1);
    ChangeUtil.addChild(parent, space2, space1.getTreeNext());
    return space1;
  }

//----------------------------------------------------------------------------------------------------

  public static final int INDENT_FACTOR = 10000; // "indent" is indent_level * INDENT_FACTOR + spaces

  public int getIndent(TreeElement element) {
    return getIndent(element, false);
  }

  public TreeElement getPrevWhitespace(final TreeElement element) {
    if (element.getTreePrev() != null) {
      TreeElement prev = element.getTreePrev();
      CompositeElement lastCompositePrev;
      while (prev instanceof CompositeElement) {
        lastCompositePrev = (CompositeElement)prev;
        prev = ((CompositeElement)prev).lastChild;
        if (prev == null) { // element.prev is "empty composite"
          return getPrevWhitespace(lastCompositePrev);
        }
      }
      if( prev.getElementType() == ElementType.WHITE_SPACE )
        return prev;
      else
        return getPrevWhitespace(prev);
    }
    else {
      if (element.getTreeParent() == null) {
        return null;
      }
      return getPrevWhitespace(element.getTreeParent());
    }
  }

  public int getIndent(final TreeElement element, boolean includeNonSpace) {
    if (element.getTreePrev() != null) {
      TreeElement prev = element.getTreePrev();
      CompositeElement lastCompositePrev;
      while (prev instanceof CompositeElement && !(prev instanceof XmlText)) {
        lastCompositePrev = (CompositeElement)prev;
        prev = ((CompositeElement)prev).lastChild;
        if (prev == null) { // element.prev is "empty composite"
          return getIndent(lastCompositePrev, includeNonSpace);
        }
      }

      String text = prev.getText();
      int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));

      if (index >= 0) {
        return getIndent(text.substring(index + 1), includeNonSpace);
      }

      if (includeNonSpace) {
        return getIndent(prev, includeNonSpace) + getIndent(text, includeNonSpace);
      }

      if (element.getElementType() == ElementType.CODE_BLOCK) {
        CompositeElement parent = element.getTreeParent();
        if (parent.getElementType() == ElementType.BLOCK_STATEMENT) {
          parent = parent.getTreeParent();
        }
        if (parent.getElementType() != ElementType.CODE_BLOCK) {
          //Q: use some "anchor" part of parent for some elements?
          // e.g. for method it could be declaration start, not doc-comment
          return getIndent(parent, includeNonSpace);
        }
      }
      else {
        if (element.getElementType() == ElementType.LBRACE) {
          return getIndent(element.getTreeParent(), includeNonSpace);
        }
      }
      //Q: any other cases?

      CompositeElement parent = prev.getTreeParent();
      TreeElement child = prev;
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
          return getIndent(prev, true) + getIndent(text, true);
        }
        else
          return getIndent(prev, includeNonSpace);
      }
    }
    else {
      if (element.getTreeParent() == null) {
        return 0;
      }
      return getIndent(element.getTreeParent(), includeNonSpace);
    }
  }

  public static int addIndent(int indent) {
    return modifyIndent(indent, INDENT_FACTOR, false);
  }

  public static int modifyIndent(int old_indent, int change, boolean absolute) {
    int indent = (absolute ? 0 : old_indent) + change;
    if (indent < 0) indent = 0;
    return indent;
  }

  public int labelIndent(int old_indent) {
    return modifyIndent(old_indent,
                        mySettings.getLabelIndentSize(getFileType()),
                        mySettings.getLabelIndentAbsolute(getFileType()));
  }

  public int addContinuationIndent(int indent) {
    return indent + mySettings.getContinuationIndentSize(getFileType());
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

//----------------------------------------------------------------------------------------------------

  public TreeElement makeHorizontalSpace(CompositeElement parent, TreeElement child1, TreeElement child2, int size) {
    return makeHorizontalSpace(parent, child1, child2, size, true);
  }

  public TreeElement makeHorizontalSpace(CompositeElement parent,
                                         TreeElement child1,
                                         TreeElement child2,
                                         int size,
                                         boolean soft) {
    if (soft) {
      int lineBreaks = getLineBreakCount(parent, child1, child2);
      if (lineBreaks > 0) {
        boolean inCode = parent.getElementType() != ElementType.JAVA_FILE && parent.getElementType() != ElementType.CLASS;
        int maxKeep = inCode ? mySettings.KEEP_BLANK_LINES_IN_CODE : mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS;
        lineBreaks = Math.min(lineBreaks, maxKeep + 1);
        if (lineBreaks == 1) {
          lineBreaks = mySettings.KEEP_LINE_BREAKS ? 1 : 0;
        }
        if (lineBreaks > 0) {
          return makeLineBreaks(parent, child1, child2, lineBreaks);
        }
      }
    }
    TreeElement leaf = child1;
    if (child1 instanceof CompositeElement) {
      ChameleonTransforming.transformChildren((CompositeElement)child1, true);
      leaf = TreeUtil.findLastLeaf(child1);
    }
    if (leaf != null && leaf.getElementType() == ElementType.END_OF_LINE_COMMENT) {
      return makeLineBreaks(parent, child1, child2, 1);
    }

    String text;
    if (size == 0) {
      text = "";
    }
    else {
      if (size == 1) {
        text = " ";
      }
      else {
        text = "";
        for (int i = 0; i < size; i++) {
          text += " ";
        }
      }
    }
    return makeSpace(parent, child1, child2, text);
  }

  public static int getElementRightSideColumn(TreeElement elem) {
    int len = 0;
    do {
      String text = elem.getText();
      int idx = text.lastIndexOf('\n');
      if (idx >= 0) {
        len += text.length() - idx;
        return len;
      }
      len += text.length();
      do {
        TreeElement prev = elem.getTreePrev();
        if (prev != null) {
          elem = prev;
          break;
        }
        elem = elem.getTreeParent();
      }
      while (elem != null);
    }
    while (elem != null);

    return len;
  }

  public TreeElement makeVerticalSpace(CompositeElement parent, TreeElement child1, TreeElement child2, int size) {
    return makeVerticalSpace(parent, child1, child2, size, true);
  }

  private TreeElement makeVerticalSpace(CompositeElement parent,
                                        TreeElement child1,
                                        TreeElement child2,
                                        int size,
                                        boolean soft) {
    if (soft) {
      boolean inCode = parent.getElementType() != ElementType.JAVA_FILE && parent.getElementType() != ElementType.CLASS;
      int maxKeep = inCode ? mySettings.KEEP_BLANK_LINES_IN_CODE : mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS;
      int lineBreaks = getLineBreakCount(parent, child1, child2);
      lineBreaks = Math.min(lineBreaks, maxKeep + 1);
      lineBreaks = Math.max(lineBreaks, size + 1);
      return makeLineBreaks(parent, child1, child2, lineBreaks);
    }
    else {
      return makeLineBreaks(parent, child1, child2, size + 1);
    }
  }

  public TreeElement makeSpace(CompositeElement parent, TreeElement child1, TreeElement child2, String text) {
    return makeSpace(parent, child1, child2, text, false);
  }

//----------------------------------------------------------------------------------------------------

  public static String getSpaceText(CompositeElement parent, TreeElement child1, TreeElement child2) {
    final LeafElement spaceElement = getSpaceElement(parent, child1, child2);
    return spaceElement != null ? spaceElement.getText() : "";
  }

  public static LeafElement getSpaceElement(CompositeElement parent, TreeElement child1, TreeElement child2) {
    final LeafElement leafElementAt = parent.findLeafElementAt(child1 != null ? child1.getStartOffsetInParent() + child1.getTextLength() : 0);
    TreeElement check = leafElementAt;
    while(check != null){
      if(check == child2) return null;
      check = check.getTreeParent();
    }
    if(leafElementAt == null || leafElementAt.getText().trim().length() > 0) return null;
    return leafElementAt;
    //
    //LeafElement space = null;
    //for (TreeElement child = child1 != null ? child1.getTreeNext() : ((parent != null) ? parent.firstChild : null);
    //     child != child2;
    //     child = child.getTreeNext()) {
    //  if (child instanceof CompositeElement && child.getTextLength() == 0) continue;
    //  LOG.assertTrue(child.getElementType() == ElementType.WHITE_SPACE);
    //  if (space != null) {
    //    LOG.assertTrue(false);
    //  }
    //  space = (LeafElement)child;
    //}
    //return space;
  }

//----------------------------------------------------------------------------------------------------

  public int getLineBreakCount(CompositeElement parent, TreeElement child1, TreeElement child2) {
    return StringUtil.getLineBreakCount(getSpaceText(parent, child1, child2));
  }

  public TreeElement makeLineBreaks(CompositeElement parent, TreeElement child1, TreeElement child2, int count) {
    StringBuffer buffer = new StringBuffer();
    String lineSeparator = "\n";
    if (child1 == null) {
      count--;
    }
    for (int i = 0; i < count; i++) {
      buffer.append(lineSeparator);
    }
    String space = getSpaceText(parent, child1, child2);
    int index = Math.max(space.lastIndexOf('\n'), space.lastIndexOf('\r'));
    buffer.append(space.substring(index + 1));
    if (count > 0 && space.length() == 0 && mySettings.INSERT_FIRST_SPACE_IN_LINE) {
      buffer.append(" "); // this prevents some elements (comments, labels) to stay at the first column
    }
    return makeSpace(parent, child1, child2, buffer.toString());
  }

//----------------------------------------------------------------------------------------------------

  public TreeElement makeSpace(CompositeElement parent,
                               TreeElement child1,
                               TreeElement child2,
                               String text,
                               boolean indentMultiline) {
    LeafElement space = getSpaceElement(parent, child1, child2);
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(parent);
    int indentShift;
    if (space == null) {
      if (text.length() == 0) return child2;
      LeafElement newSpace = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, text.toCharArray(), 0, text.length(),
                                                             charTableByTree, null);
      ChangeUtil.addChild(parent, newSpace, child1 != null ? child1.getTreeNext() : parent.firstChild);
      indentShift = getIndent(newSpace.getText(), true);
    }
    else {
      final String oldSpace = space.getText();
      if (text.length() == 0) {
        if (child1 != null && child2 != null) {
          if (!canStickChildrenTogether(child1, child2)) {
            return makeSpace(parent, child1, child2, " ", indentMultiline);
          }
        }

        ChangeUtil.removeChild(parent, space);
        indentShift = -getIndent(oldSpace, true);
      }
      else {
        if (text.length() == space.getTextLength()) {
          int i;
          for (i = 0; i < text.length(); i++) {
            if (text.charAt(i) != space.charAt(i)) break;
          }
          if (i == text.length()) return child2;
        }
        TreeElement newSpace = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, text.toCharArray(), 0, text.length(),
                                                               charTableByTree, null);
        ChangeUtil.replaceChild(space.getTreeParent(), space, newSpace);
        indentShift = getIndent(newSpace.getText(), true) - getIndent(oldSpace, true);
      }
    }
    if (indentMultiline) {
      child2 = shiftIndentInside(child2, indentShift);
    }
    return child2;
  }

  public boolean canStickChildrenTogether(TreeElement child1, TreeElement child2) {
    LeafElement token1 = TreeUtil.findLastLeaf(child1);
    LeafElement token2 = TreeUtil.findFirstLeaf(child2);
    LOG.assertTrue(token1 != null);
    LOG.assertTrue(token2 != null);
    if (token1.getElementType() instanceof IJavaElementType && token2.getElementType() instanceof IJavaElementType) {
      return canStickJavaTokens((PsiJavaToken)SourceTreeToPsiMap.treeElementToPsi(token1),
                                (PsiJavaToken)SourceTreeToPsiMap.treeElementToPsi(token2));
    }
    else {
      return true; //?
    }
  }

  private Map<Pair<IElementType, IElementType>, Boolean> myCanStickJavaTokensMatrix = new HashMap<Pair<IElementType, IElementType>, Boolean>();

  public boolean canStickJavaTokens(PsiJavaToken token1, PsiJavaToken token2) {
    IElementType type1 = token1.getTokenType();
    IElementType type2 = token2.getTokenType();

    Pair<IElementType, IElementType> pair = new Pair<IElementType, IElementType>(type1, type2);
    Boolean res = myCanStickJavaTokensMatrix.get(pair);
    if (res == null) {
      String text = token1.getText() + token2.getText();
      final LanguageLevel languageLevel = getProject() != null
                                          ? LanguageLevel.HIGHEST
                                          : PsiManager.getInstance(getProject()).getEffectiveLanguageLevel();
      Lexer lexer = new JavaLexer(languageLevel);
      lexer.start(text.toCharArray(), 0, text.length());
      boolean canMerge = lexer.getTokenType() == type1;
      res = Boolean.valueOf(canMerge);
      myCanStickJavaTokensMatrix.put(pair, res);
    }
    return res.booleanValue();
  }

  public TreeElement normalizeIndent( final TreeElement dst ) {
    if( !(dst instanceof CompositeElement) ) return dst;

    int newIndent = getIndent(dst);

    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(dst).getContainingFile();
    FileElement fileElement = ((FileElement)SourceTreeToPsiMap.psiElementToTree(file));
    CharTable table = fileElement.getCharTable();
    indentSubtree((CompositeElement)dst, 0, newIndent, table);

    return dst;
  }

  public void indentSubtree( final CompositeElement tree, final int oldIndent, final int newIndent, CharTable table) {
    if( oldIndent == newIndent ) return;

    for( TreeElement son = tree.firstChild; son != null; ) {
      if( son.getElementType() == ElementType.WHITE_SPACE ) {
        final int indentLevelsDiff = newIndent/Helper.INDENT_FACTOR - oldIndent/Helper.INDENT_FACTOR;
        final int indentSpacesDiff = newIndent%Helper.INDENT_FACTOR - oldIndent%Helper.INDENT_FACTOR;

        final String ws = son.getText();
        String newIndentString = indentWhitespace(ws, indentLevelsDiff, indentSpacesDiff);

        if( !ws.equals(newIndentString) ) {
          TreeElement newWSElem = Factory.createSingleLeafElement(ElementType.WHITE_SPACE,
                                                                  newIndentString.toCharArray(),
                                                                  0, newIndentString.length(), table, null);
          ChangeUtil.replaceChild((CompositeElement)tree, son, newWSElem);
          son = newWSElem;
        }
      }
      else if( son instanceof CompositeElement ) {
        indentSubtree( (CompositeElement) son, oldIndent, newIndent, table);
      }
      son = son.getTreeNext();
    }
  }

  public TreeElement shiftIndentInside(TreeElement element, int indentShift) {
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

        TreeElement leaf = element.findLeafElementAt(offset);
        if (leaf.getElementType() != ElementType.WHITE_SPACE
            && leaf.getElementType() != ElementType.C_STYLE_COMMENT
            && leaf.getElementType() != ElementType.JSP_TEMPLATE_DATA
            && leaf.getElementType() != ElementType.JSP_DIRECTIVE_WHITE_SPACE
            && leaf.getElementType() != ElementType.JSP_ACTION_WHITE_SPACE
            && leaf.getElementType() != ElementType.DOC_COMMENT_DATA
            && leaf.getElementType() != ElementType.XML_DATA_CHARACTERS
            && leaf.getElementType() != ElementType.XML_ATTRIBUTE_VALUE_TOKEN
            && leaf.getElementType() != ElementType.XML_COMMENT_CHARACTERS) {
          LOG.error("Error",
                    new String[]{
                      leaf.getElementType().toString(),
                      "Type: " + leaf.getElementType() + " text: " + leaf.getText()
                    });
        }

        if (offset1 < text.length()) {
          TreeElement next = element.findLeafElementAt(offset1);
          if ((next.getElementType() == ElementType.END_OF_LINE_COMMENT || next.getElementType() == ElementType.C_STYLE_COMMENT) &&
              next != element) {
            if (mySettings.KEEP_FIRST_COLUMN_COMMENT) {
              int commentIndent = getIndent(next, true);
              if (commentIndent == 0) continue;
            }
          }
          else if (next.getElementType() == ElementType.XML_DATA_CHARACTERS) {
            continue;
          }
        }

        int leafOffset = getStartOffset(element, leaf);
        if (leaf.getElementType() == ElementType.DOC_COMMENT_DATA && leafOffset + leaf.getTextLength() == offset + 1) {
          TreeElement next = element.findLeafElementAt(offset + 1);
          if (next.getElementType() == ElementType.WHITE_SPACE) {
            leaf = next;
            leafOffset = getStartOffset(element, leaf);
          }
          else {
            if (newSpace.length() > 0) {
              LeafElement newLeaf = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, newSpace.toCharArray(), 0,
                                                                    newSpace.length(), charTableByTree, null);
              ChangeUtil.addChild(next.getTreeParent(), newLeaf, next);
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
          LeafElement newLeaf = Factory.createSingleLeafElement(leaf.getElementType(), newLeafText.toCharArray(), 0,
                                                                newLeafText.length(), charTableByTree, null);
          if (leaf.getTreeParent() != null) {
            ChangeUtil.replaceChild(leaf.getTreeParent(), leaf, newLeaf);
          }
          if (leaf == element) {
            element = newLeaf;
          }
        }
        else {
          CompositeElement parent = leaf.getTreeParent();
          if (parent != null) {
            ChangeUtil.removeChild(parent, leaf);
          }
        }
        text = text.substring(0, offset + 1) + newSpace + text.substring(offset1);
      }
    }
    return element;
  }

  public boolean isSpaceAtStartOfLine(CompositeElement parent, TreeElement child1, TreeElement child2) {
    String space = getSpaceText(parent, child1, child2);
    if (space.indexOf('\n') >= 0 || space.indexOf('\r') >= 0) return true;
    if (child1 != null) {
      String text = child1.getText();
      char c = text.charAt(text.length() - 1);
      if (c == '\n' || c == '\r') return true;
    }
    //if (parent.parent == null && child1 == null && child2.getTextRange().getStartOffset() == space.length()) { // at the beginning of file
    if (SourceTreeToPsiMap.treeElementToPsi(parent) instanceof PsiFile && child1 == null) { // at the beginning of file
      return true;
    }
    return false;
  }

  private int getIndentOffset( int indent ) {
    return indent % INDENT_FACTOR + (indent / INDENT_FACTOR) * mySettings.getIndentSize(getFileType());
  }

  public String indentWhitespace( String whitespace, int indentLevelsDiff, int indentSpacesDiff ) {
    final int posLF = whitespace.lastIndexOf('\n');
    if( (indentLevelsDiff == 0 && indentSpacesDiff == 0) || posLF < 0 ) return whitespace;

    final int oldIndent = getIndent(whitespace, false);

    int newLevels = oldIndent / INDENT_FACTOR + indentLevelsDiff;
    int newSpaces = oldIndent % INDENT_FACTOR + indentSpacesDiff;

    if( newLevels < 0 ) {
      newSpaces -= (-newLevels) * mySettings.getIndentSize(getFileType());
      newLevels = 0;
    }

    if( newSpaces < 0 ) {
      final int levels = (-newSpaces) / mySettings.getIndentSize(getFileType());
      newLevels -= levels + 1;
      newSpaces += (levels + 1) * mySettings.getIndentSize(getFileType());

      if( newLevels < 0 ) { // Too large unindent... Indentation has to be broken
        newSpaces = 0;
        newLevels = 0;
      }
    }

    return whitespace.substring(0,posLF+1) + fillIndent(newLevels * INDENT_FACTOR + newSpaces);
  }

  public FileType getFileType() {
    return myFileType;
  }

  public Project getProject() {
    return myProject;
  }
}
