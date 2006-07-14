package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.IndentInfo;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CodeEditUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeEditUtil");
  public static final Key<IndentInfo> INDENT_INFO_KEY = new Key<IndentInfo>("IndentInfo");
  public static final Key<Boolean> GENERATED_FLAG = new Key<Boolean>("CREATED BY IDEA");
  private static final Key<Integer> INDENT_INFO = new Key<Integer>("INDENTATION");
  private static final Key<Boolean> REFORMAT_KEY = new Key<Boolean>("REFORMAT BEFORE THIS ELEMENT");

  private CodeEditUtil() {
  }

  public static void addChild(CompositeElement parent, ASTNode child, ASTNode anchorBefore) {
    addChildren(parent, child, child, anchorBefore);
  }

  public static void removeChild(CompositeElement parent, ASTNode child) {
    removeChildren(parent, child, child);
  }

  public static ASTNode addChildren(CompositeElement parent, ASTNode first, ASTNode last, ASTNode anchorBefore) {
    LOG.assertTrue(first != null);
    LOG.assertTrue(last != null);
    ASTNode lastChild = last.getTreeNext();
    {
      ASTNode current = first;
      while(current != lastChild){
        saveWhitespacesInfo(current);
        checkForOuters(current);
        current = current.getTreeNext();
      }
    }
    parent.addChildren(first, lastChild, anchorBefore);
    final ASTNode firstAddedLeaf = findFirstLeaf(first, last);
    final ASTNode prevLeaf = TreeUtil.prevLeaf(first);
    if(firstAddedLeaf != null){
      ASTNode placeHolderEnd = makePlaceHolderBetweenTokens(prevLeaf, firstAddedLeaf, isFormattingRequiered(prevLeaf, first), false);
      if(placeHolderEnd != prevLeaf && first == firstAddedLeaf) first = placeHolderEnd;
      final ASTNode lastAddedLeaf = findLastLeaf(first, last);
      placeHolderEnd = makePlaceHolderBetweenTokens(lastAddedLeaf, TreeUtil.nextLeaf(last), true, false);
      if(placeHolderEnd != lastAddedLeaf && lastAddedLeaf == first) first = placeHolderEnd;
    }
    else makePlaceHolderBetweenTokens(prevLeaf, TreeUtil.nextLeaf(last), isFormattingRequiered(prevLeaf, first), false);
    return first;
  }

  private static boolean isFormattingRequiered(final ASTNode prevLeaf, ASTNode first) {
    while(first != null) {
      ASTNode current = prevLeaf;
      while (current != null) {
        if (current.getTreeNext() == first) return true;
        current = current.getTreeParent();
      }
      final ASTNode parent = first.getTreeParent();
      if (parent != null && parent.getTextRange().equals(first.getTextRange())) {
        first = parent;
      }
      else {
        break;
      }
    }
    return false;
  }

  public static void checkForOuters(final ASTNode element) {
    if (element instanceof OuterLanguageElement) throw new TooComplexPSIModificationException();
    if (element instanceof CompositeElement) {
      TreeElement child = ((CompositeElement)element).firstChild;
      while (child != null) {
        checkForOuters(child);
        child = child.next;
      }
    }
  }

  public static void saveWhitespacesInfo(final ASTNode first) {
    if(first == null || isNodeGenerated(first) || getOldIndentation(first) >= 0) return;
    final PsiFile containingFile = first.getPsi().getContainingFile();
    final Helper helper = new Helper(containingFile.getFileType(), containingFile.getProject());
    setOldIndentation((TreeElement)first, helper.getIndent(first));
  }

  public static int getOldIndentation(ASTNode node){
    if(node == null) return -1;
    final Integer stored = node.getCopyableUserData(INDENT_INFO);
    return stored != null ? stored : -1;
  }

  public static void removeChildren(CompositeElement parent, ASTNode first, ASTNode last) {
    LOG.assertTrue(first != null);
    LOG.assertTrue(last != null);
    final boolean tailingElement = last.getStartOffset() + last.getTextLength() == parent.getStartOffset() + parent.getTextLength();
    final boolean forceReformat = needToForceReformat(parent, first, last);
    saveWhitespacesInfo(first);

    TreeElement child = (TreeElement)first;
    while (child != null) {
      checkForOuters(child);
      if (child == last) break;
      child = child.next;
    }

    final ASTNode prevLeaf = TreeUtil.prevLeaf(first);
    final ASTNode nextLeaf = TreeUtil.nextLeaf(last);
    parent.removeRange(first, last.getTreeNext());
    makePlaceHolderBetweenTokens(prevLeaf, nextLeaf, forceReformat, tailingElement);
  }

  private static boolean needToForceReformat(final CompositeElement parent, final ASTNode first, final ASTNode last) {
    return parent == null || first.getStartOffset() != parent.getStartOffset() ||
           parent.getText().trim().length() == getTrimmedTextLength(first, last) && needToForceReformat(parent.getTreeParent(), parent, parent);
  }

  private static int getTrimmedTextLength(ASTNode first, final ASTNode last) {
    final StringBuffer buffer = new StringBuffer();
    while(first != last.getTreeNext()) {
      buffer.append(first.getText());
      first = first.getTreeNext();
    }
    return buffer.toString().trim().length();
  }

  public static void replaceChild(CompositeElement parent, ASTNode oldChild, ASTNode newChild) {
    saveWhitespacesInfo(oldChild);
    saveWhitespacesInfo(newChild);
    checkForOuters(oldChild);
    checkForOuters(newChild);
    parent.replaceChild(oldChild, newChild);
    final LeafElement firstLeaf = TreeUtil.findFirstLeaf(newChild);
    final ASTNode prevToken = TreeUtil.prevLeaf(newChild);
    if(firstLeaf != null){
      final ASTNode nextLeaf = TreeUtil.nextLeaf(newChild);
      makePlaceHolderBetweenTokens(prevToken, firstLeaf, isFormattingRequiered(prevToken, newChild), false);
      if(nextLeaf != null)
        makePlaceHolderBetweenTokens(TreeUtil.prevLeaf(nextLeaf), nextLeaf, false, false);
    }
    else makePlaceHolderBetweenTokens(prevToken, TreeUtil.nextLeaf(newChild), isFormattingRequiered(prevToken, newChild), false);
  }

  private static ASTNode findFirstLeaf(ASTNode first, ASTNode last) {
    do{
      final LeafElement leaf = TreeUtil.findFirstLeaf(first);
      if(leaf != null) return leaf;
      first = first.getTreeNext();
    }
    while(first != last);
    return null;
  }

  private static ASTNode findLastLeaf(ASTNode first, ASTNode last) {
    do{
      final LeafElement leaf = TreeUtil.findLastLeaf(last);
      if(leaf != null) return leaf;
      last = last.getTreePrev();
    }
    while(first != last);
    return null;
  }

  private static ASTNode makePlaceHolderBetweenTokens(ASTNode left, final ASTNode right, boolean forceReformat, final boolean normalizeTailingWhitespace) {
    if(right == null) return left;

    markToReformatBefore(right, false);
    if(left == null){
      markToReformatBefore(right, true);
    }
    else if(left.getElementType() == ElementType.WHITE_SPACE && left.getTreeNext() == null && normalizeTailingWhitespace){
      // handle tailing whitespaces if element on the left has been removed
      left.getTreeParent().removeChild(left);
      markToReformatBeforeOrInsertWhitespace(left, right, right.getTreeParent().getPsi().getManager());
      left = right;
    }
    else if(left.getElementType() == ElementType.WHITE_SPACE && right.getElementType() == ElementType.WHITE_SPACE) {
      final String text;
      final int leftBlankLines = getBlankLines(left.getText());
      final int rightBlankLines = getBlankLines(right.getText());
      final boolean leaveRightText = leftBlankLines < rightBlankLines;
      if (leftBlankLines == 0 && rightBlankLines == 0) text = left.getText() + right.getText();
      else if (leaveRightText) text = right.getText();
      else text = left.getText();
      if(leaveRightText || forceReformat){
        final LeafElement merged =
          Factory.createSingleLeafElement(ElementType.WHITE_SPACE, text.toCharArray(), 0, text.length(), null, left.getPsi().getManager());
        if(!leaveRightText){
          left.getTreeParent().replaceChild(left, merged);
          right.getTreeParent().removeChild(right);
        }
        else {
          right.getTreeParent().replaceChild(right, merged);
          left.getTreeParent().removeChild(left);
        }
        left = merged;
      }
      else right.getTreeParent().removeChild(right);
    }
    else if(left.getElementType() != ElementType.WHITE_SPACE || forceReformat){
      if(right.getElementType() == ElementType.WHITE_SPACE){
        markWhitespaceForReformat(right);
      }
      else if(left.getElementType() == ElementType.WHITE_SPACE){
        markWhitespaceForReformat(left);
      }
      else markToReformatBeforeOrInsertWhitespace(left, right, right.getTreeParent().getPsi().getManager());
    }
    return left;
  }

  private static void markWhitespaceForReformat(final ASTNode right) {
    final String text = right.getText();
    final LeafElement merged = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, text.toCharArray(), 0, text.length(), null,
                                                               right.getPsi().getManager());
    right.getTreeParent().replaceChild(right, merged);
  }

  private static void markToReformatBeforeOrInsertWhitespace(final ASTNode left, @NotNull final ASTNode right, PsiManager manager) {
    final Language leftLang = left != null ? left.getElementType().getLanguage() : null;
    final Language rightLang = right.getElementType().getLanguage();
    final ParserDefinition parserDefinition = rightLang.getParserDefinition();
    LeafElement generatedWhitespace = null;
    if(leftLang == rightLang && parserDefinition != null){
      //noinspection EnumSwitchStatementWhichMissesCases
      switch(parserDefinition.spaceExistanceTypeBetweenTokens(left, right)){
        case MUST:
          generatedWhitespace = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, new char[]{' '}, 0, 1, null, manager);
          break;
        case MUST_LINE_BREAK:
          generatedWhitespace = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, new char[]{'\n'}, 0, 1, null, manager);
          break;
        default:
          generatedWhitespace = null;
      }
    }
    if(generatedWhitespace != null){
      final ParseUtil.CommonParentState parentState = new ParseUtil.CommonParentState();
      ParseUtil.prevLeaf((TreeElement)right, parentState);
      parentState.nextLeafBranchStart.getTreeParent().addChild(generatedWhitespace, parentState.nextLeafBranchStart);
    }
    else markToReformatBefore(right, true);
  }

  public static void markToReformatBefore(final ASTNode right, boolean value) {
    if (value) right.putCopyableUserData(REFORMAT_KEY, true);
    else right.putCopyableUserData(REFORMAT_KEY, null);
  }

  private static int getBlankLines(final String text) {
    int result = 0;
    int currentIndex = -1;
    while((currentIndex = text.indexOf('\n', currentIndex + 1)) >= 0) result++;
    return result;
  }

  private static boolean isWS(final ASTNode lastChild) {
    return lastChild != null && lastChild.getElementType() == ElementType.WHITE_SPACE;
  }

  public static boolean canStickChildrenTogether(final ASTNode child1, final ASTNode child2) {
    if (child1 == null || child2 == null) return true;
    if (isWS(child1) || isWS(child2)) return true;

    ASTNode token1 = TreeUtil.findLastLeaf(child1);
    ASTNode token2 = TreeUtil.findFirstLeaf(child2);

    LOG.assertTrue(token1 != null);
    LOG.assertTrue(token2 != null);

    return !(token1.getElementType()instanceof IJavaElementType && token2.getElementType()instanceof IJavaElementType) ||
           canStickJavaTokens((PsiJavaToken)SourceTreeToPsiMap.treeElementToPsi(token1),
                              (PsiJavaToken)SourceTreeToPsiMap.treeElementToPsi(token2));

  }

  private static Map<Pair<IElementType, IElementType>, Boolean> myCanStickJavaTokensMatrix =
    new HashMap<Pair<IElementType, IElementType>, Boolean>();

  private static boolean canStickJavaTokens(PsiJavaToken token1, PsiJavaToken token2) {
    IElementType type1 = token1.getTokenType();
    IElementType type2 = token2.getTokenType();

    Pair<IElementType, IElementType> pair = new Pair<IElementType, IElementType>(type1, type2);
    Boolean res = myCanStickJavaTokensMatrix.get(pair);
    if (res == null) {
      if (!checkToken(token1) || !checkToken(token2)) return true;
      String text = token1.getText() + token2.getText();
      Lexer lexer = new JavaLexer(LanguageLevel.HIGHEST);
      lexer.start(text.toCharArray(), 0, text.length());
      boolean canMerge = lexer.getTokenType() == type1;
      lexer.advance();
      canMerge &= lexer.getTokenType() == type2;
      res = canMerge;
      myCanStickJavaTokensMatrix.put(pair, res);
    }
    return res.booleanValue();
  }

  private static boolean checkToken(final PsiJavaToken token1) {
    Lexer lexer = new JavaLexer(LanguageLevel.HIGHEST);
    final String text = token1.getText();
    lexer.start(text.toCharArray(), 0, text.length());
    if (lexer.getTokenType() != token1.getTokenType()) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public static String getStringWhiteSpaceBetweenTokens(ASTNode first, ASTNode second, Language language) {
    final FormattingModelBuilder modelBuilder = language.getFormattingModelBuilder();
    if (modelBuilder == null) {
      final LeafElement leafElement = ParseUtil.nextLeaf((TreeElement)first, null);
      if (leafElement != second) {
        return leafElement.getText();
      }
      else {
        return null;
      }
    }
    else {
      final PsiFile file = (PsiFile)TreeUtil.getFileElement((TreeElement)second).getPsi();
      final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings();
      return getWhiteSpaceBeforeToken(second, language, true).generateNewWhiteSpace(settings.getIndentOptions(file.getFileType()));
    }

  }

  public static IndentInfo getWhiteSpaceBeforeToken(final ASTNode tokenNode, final Language language, final boolean mayChangeLineFeeds) {
    LOG.assertTrue(tokenNode != null);
    final PsiFile file = (PsiFile)TreeUtil.getFileElement((TreeElement)tokenNode).getPsi();
    final Project project = file.getProject();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    final int tokenStartOffset = tokenNode.getStartOffset();

    final boolean oldValue = settings.XML_KEEP_LINE_BREAKS;
    final int oldKeepBlankLines = settings.XML_KEEP_BLANK_LINES;
    settings.XML_KEEP_BLANK_LINES = 0;
    try {
      final FormattingModelBuilder builder = language.getFormattingModelBuilder();
      final PsiElement element = file.findElementAt(tokenStartOffset);

      if (builder != null && element.getLanguage().getFormattingModelBuilder() != null) {

        final TextRange textRange = element.getTextRange();
        final FormattingModel model = builder.createModel(file, settings);
        return FormatterEx.getInstanceEx().getWhiteSpaceBefore(model.getDocumentModel(), model.getRootBlock(), settings,
                                                               settings.getIndentOptions(file.getFileType()), textRange,
                                                               mayChangeLineFeeds);
      }
      else {
        return new IndentInfo(0, 0, 0);
      }

    }
    finally {
      settings.XML_KEEP_LINE_BREAKS = oldValue;
      settings.XML_KEEP_BLANK_LINES = oldKeepBlankLines;
    }
  }

  public static boolean isNodeGenerated(final ASTNode node) {
    return node == null || node.getCopyableUserData(GENERATED_FLAG) != null;
  }

  public static void setNodeGenerated(final ASTNode next, final boolean value) {
    if(next == null) return;
    if(value) next.putCopyableUserData(GENERATED_FLAG, true);
    else next.putCopyableUserData(GENERATED_FLAG, null);
  }

  public static void setOldIndentation(final TreeElement treeElement, final int oldIndentation) {
    if(treeElement == null) return;
    if(oldIndentation >= 0) treeElement.putCopyableUserData(INDENT_INFO, oldIndentation);
    else treeElement.putCopyableUserData(INDENT_INFO, null);
  }

  public static boolean isMarkedToReformatBefore(final TreeElement element) {
    return element.getCopyableUserData(REFORMAT_KEY) != null;
  }
}
