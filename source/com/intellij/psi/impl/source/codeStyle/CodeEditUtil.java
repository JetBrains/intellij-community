package com.intellij.psi.impl.source.codeStyle;

import com.intellij.codeFormatting.PseudoText;
import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.newCodeFormatting.Formatter;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.newCodeFormatting.IndentInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CodeEditUtil {
  
  private static final boolean DO_OUTPUT = false;
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeEditUtil");
  public static final Key<IndentInfo> INDENT_INFO_KEY = new Key<IndentInfo>("IndentInfo");

  public static ASTNode addChild(CompositeElement parent, ASTNode child, ASTNode anchorBefore) {
    return addChildren(parent, child, child, anchorBefore);
  }

  public static TreeElement addChildren(CompositeElement parent, ASTNode first, ASTNode last, ASTNode anchorBefore) {

    checkAllWhiteSpaces(parent);
    
    if (DO_OUTPUT) {
      System.out.println("CodeEditUtil.addChildrenBefore\n" + parent.getPsi().getContainingFile().getText());
    }
    ASTNode lastChild = last != null ? last.getTreeNext() : null;

    ASTNode nextElement = saveIndentsBeforeAdd(first, lastChild, anchorBefore, parent);

    first = trimWhiteSpaces(first, lastChild);

    if (first == null) {
      return null;
    }

    boolean keepFirstIndent = false;

    final ASTNode elemBeforeAnchor = getElementBeforeAnchor(parent, anchorBefore);

    if (elemBeforeAnchor != null) {
      ASTNode firstNonEmpty = findFirstNonEmpty(first, lastChild, parent, anchorBefore);
      if (firstNonEmpty == null || mustKeepFirstIndent(elemBeforeAnchor, parent)){
        keepFirstIndent = true;
      }
    }


    parent.addChildren(first, lastChild, anchorBefore);
    final List<ASTNode> treePrev = getPreviousElements(first);
    adjustWhiteSpaces(first, anchorBefore, keepFirstIndent);
    if (nextElement != null) {
      adjustWhiteSpaces(nextElement, nextElement.getTreeNext(), false);
    }

    checkAllWhiteSpaces(parent);
    if (DO_OUTPUT) {
      System.out.println("CodeEditUtil.addChildren\n" + parent.getPsi().getContainingFile().getText());
    }
    if (treePrev == null) {
      return (TreeElement)parent.getFirstChildNode();
    } else {
      ASTNode firstValid = findFirstValid(treePrev);
      if (firstValid == null) {
        return (TreeElement)parent.getFirstChildNode();
      } else {
        return (TreeElement)firstValid.getTreeNext();
      }
    }
  }

  private static ASTNode saveIndentsBeforeAdd(final ASTNode first,
                                              final ASTNode lastChild,
                                              final ASTNode anchorBefore,
                                              final CompositeElement parent) {
    saveIndents(first, lastChild);
    ASTNode nextElement = anchorBefore;
    if (nextElement == null) {
      nextElement = findElementAfter(parent, false);
    } else if (isWS(nextElement)) {
      nextElement = findElementAfter(anchorBefore, false);
    }
    if (nextElement != null) {
      saveIndents(nextElement);
    }
    return nextElement;
  }

  private static ASTNode findFirstNonEmpty(final ASTNode first,
                                           final ASTNode last,
                                           final CompositeElement parent,
                                           final ASTNode anchorBefore) {
    ASTNode firstNonEmpty = first;
    while (firstNonEmpty != null && firstNonEmpty != last && firstNonEmpty.getTextLength() == 0) {
      firstNonEmpty = firstNonEmpty.getTreeNext();
    }
    if (firstNonEmpty == null && anchorBefore != null) {
      firstNonEmpty = TreeUtil.findFirstLeaf(anchorBefore);
      while (firstNonEmpty != null && firstNonEmpty.getTextLength() == 0) {
        firstNonEmpty = firstNonEmpty.getTreeNext();
      }
    }
    if (firstNonEmpty == null) {
      firstNonEmpty = TreeUtil.nextLeaf(parent);
    }
    return firstNonEmpty;
  }

  private static boolean mustKeepFirstIndent(final ASTNode elementBeforeChange, final CompositeElement parent) {
    if (elementBeforeChange == null) return true;
    if (elementBeforeChange.getElementType() != ElementType.WHITE_SPACE) {
      return false;
    }
    return elementBeforeChange.getTextRange().getStartOffset() < parent.getTextRange().getStartOffset();
  }

  private static ASTNode getElementBeforeAnchor(final CompositeElement parent, final ASTNode anchorBefore) {
    if (anchorBefore != null) {
      return TreeUtil.prevLeaf(anchorBefore);
    }

    final ASTNode lastChild = parent.getLastChildNode();
    if (lastChild != null) {
      if (lastChild.getTextLength() > 0) {
        return lastChild;
      } else {
        return TreeUtil.prevLeaf(lastChild);
      }
    } else {
      return TreeUtil.prevLeaf(parent);
    }

  }

  private static void checkAllWhiteSpaces(final ASTNode parent) {
    /*
    if (CodeStyleSettingsManager.getSettings(parent.getPsi().getProject()).JAVA_INDENT_OPTIONS.INDENT_SIZE == 2) {
      LOG.assertTrue(false);
    }
    */
    ASTNode node = parent.getPsi().getContainingFile().getNode();
    ASTNode leaf = TreeUtil.findFirstLeaf(node);
    while (leaf != null) {
      if (isWS(leaf) && whiteSpaceHasInvalidPosition(leaf)) {
        LOG.assertTrue(false);
      }
      leaf = TreeUtil.nextLeaf(leaf);
    }
  }

  public static void removeChild(CompositeElement parent, ASTNode child) {
    removeChildren(parent, child, child);
  }

  public static void removeChildren(CompositeElement parent, ASTNode first, ASTNode last) {
    if (DO_OUTPUT) {
      System.out.println("CodeEditUtil.removeChildrenBefore\n" + parent.getPsi().getContainingFile().getText());
    }
    checkAllWhiteSpaces(parent);
    
    ASTNode lastChild = last == null ? null : last.getTreeNext();
    final ASTNode prevElement = TreeUtil.prevLeaf(first);
    final ASTNode nextElement = findElementAfter(last == null ? parent : last, false);
    if (nextElement != null) {
      saveIndents(nextElement);
    }

    boolean adjustWSBefore = containLineBreaks(first, lastChild);

    if (!mustKeepFirstIndent(prevElement, parent)) {
      adjustWSBefore = true; 
    }
    
    parent.removeRange(first, lastChild);
    
    if (!adjustWSBefore && parent.getTextLength() == 0 && prevElement != null && isWS(prevElement) && !prevElement.textContains('\n')) {
      adjustWSBefore = true;
    }

    final PsiFile file = parent.getPsi().getContainingFile();
    final CodeStyleSettings.IndentOptions options = CodeStyleSettingsManager.getSettings(file.getProject())
      .getIndentOptions(file.getFileType());

    if (nextElement != null) {

      ASTNode elementBeforeNext = TreeUtil.prevLeaf(nextElement);

      if (prevElement != null && isWS(prevElement) && isWS(elementBeforeNext) && prevElement != elementBeforeNext) {
        if (!elementBeforeNext.textContains('\n') && prevElement.textContains('\n')) {
          /*
              void foo1(){}
              static void foo2(){}
              remove static
          */
          delete(elementBeforeNext);
        } else {
          final String text = composeNewWS(prevElement.getText(), elementBeforeNext.getText(), options);
          delete(elementBeforeNext);
          replace(prevElement, text);
        }
      }


      elementBeforeNext = TreeUtil.prevLeaf(nextElement);

      if (isWS(elementBeforeNext) && whiteSpaceHasInvalidPosition(elementBeforeNext)) {
        final String text = elementBeforeNext.getText();        
        delete(elementBeforeNext);
        FormatterUtil.replaceWhiteSpace(text,
                                        nextElement,
                                        ElementType.WHITE_SPACE);
      }
      
      elementBeforeNext = TreeUtil.prevLeaf(nextElement);
      
      if (elementBeforeNext != null && isWS(elementBeforeNext) && elementBeforeNext.getTextRange().getStartOffset() == 0) {
        final IndentInfo info = createInfo(elementBeforeNext.getText(), options);
        if (info.getLineFeeds() > 0) {
          final String text = new IndentInfo(0,info.getIndentSpaces(), info.getSpaces()).generateNewWhiteSpace(options);
          replace(elementBeforeNext, text);
        }
      }


      if (adjustWSBefore) {
        adjustWhiteSpaceBefore(nextElement, true, true, true, false);
      }

    } else {
      final ASTNode fileNode = SourceTreeToPsiMap.psiElementToTree(parent.getPsi().getContainingFile());
      ASTNode lastLeaf = TreeUtil.findLastLeaf(fileNode);
      if (isWS(lastLeaf)) {
        delete(lastLeaf);
      }
    }

    checkAllWhiteSpaces(parent);
    if (DO_OUTPUT) {
      System.out.println("CodeEditUtil.removeChildren\n" + parent.getPsi().getContainingFile().getText());
    }
    //removeChildrenOld(parent,first, last);
  }

  private static void runTransaction(final ASTNode prevElement, final ASTNode elementBeforeNext1, final Runnable action) {
    final PomModel model = prevElement.getPsi().getProject().getModel();
    try {

      model.runTransaction(
        new PomTransactionBase(TreeUtil.findCommonParent(elementBeforeNext1, prevElement).getPsi()) {
          public PomModelEvent runInner() {
            action.run();
            return null;
          }
        }, model.getModelAspect(TreeAspect.class));
    } catch (IncorrectOperationException e){

    }
  }

  private static void replace(final ASTNode element, final String text) {
    if (!text.equals(element.getText())){
      final CharTable charTable = SharedImplUtil.findCharTableByTree(element);
      LeafElement newWhiteSpace = Factory.createSingleLeafElement(element.getElementType(),
                                                                  text.toCharArray(), 0, text.length(),
                                                                  charTable,
                                                                  SharedImplUtil.getManagerByTree(element));

      element.getTreeParent().replaceChild(element,
                                           newWhiteSpace);
    }
  }


  private static boolean hasNonEmptyNext(final ASTNode element) {
    ASTNode current = element.getTreeNext();
    while (current != null) {
      if (current.getTextLength() > 0) {
        return true;
      }
      if (current.getElementType() == ElementType.WHITE_SPACE) {
        return false;
      }
      current = current.getTreeNext();
    }
    if (element.getTextRange().getEndOffset() == element.getPsi().getContainingFile().getTextLength()) {
      return element.getTreeNext() == null;
    }
    return false;
  }

  private static boolean hasNonEmptyPrev(ASTNode element) {
    if (!element.getPsi().isValid()) {
      LOG.assertTrue(false);
    }
    ASTNode current = element.getTreePrev();
    while (current != null) {
      if (current.getTextLength() > 0) {
        return true;
      }
      if (current.getElementType() == ElementType.WHITE_SPACE) {
        return false;
      }
      current = current.getTreePrev();
    }
    if (element.getTextRange().getStartOffset() == 0) return element.getTreePrev() == null;
    return false;
  }

  private static boolean whiteSpaceHasInvalidPosition(final ASTNode element) {
    final ASTNode treeParent = element.getTreeParent();
    if (treeParent != null) {
      if (isWS(element) && treeParent.getElementType() == ElementType.XML_TEXT) return false;
      if (treeParent.getPsi().getUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY) != null) return false;
      if (treeParent.getElementType() == ElementType.XML_PROLOG) return false;      
    }
    if (hasNonEmptyPrev(element) && hasNonEmptyNext(element)) return false;
    if (treeParent != null) {
      if (treeParent.getElementType() instanceof IChameleonElementType) {
        if (((IChameleonElementType)treeParent.getElementType()).isParsable(treeParent.getText(), element.getPsi().getProject())){
          return false;
        } else {
          return true;
        }
      } else {
        return true;
      }
    } else {
      return true;
    }
  }

  private static boolean containLineBreaks(final ASTNode first, final ASTNode last) {
    ASTNode current = first;
    while (current != last) {
      if (current.textContains('\n')) return true;
      current = current.getTreeNext();
    }
    return false;
  }

  private static void saveIndents(final ASTNode first, final ASTNode lastChild) {
    final PsiFile file = first.getPsi().getContainingFile();
    final Language language = file.getLanguage();
    final FormattingModelBuilder builder = language.getFormattingModelBuilder();
    if (builder != null) {
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(file.getProject());
      final FormattingModel model = builder.createModel(file, settings);
      ASTNode current = first;
      while (current != lastChild) {
        final IElementType elementType = current.getElementType();
        if (elementType != ElementType.WHITE_SPACE
            && elementType != ElementType.DOC_COMMENT_LEADING_ASTERISKS
            && elementType != ElementType.DOC_TAG
            && current.getTextLength() > 0) {
          Formatter.getInstance().saveIndents(model, current.getTextRange(),
                                              new MyIndentInfoStorage(file),
                                              settings,
                                              settings.getIndentOptions(file.getFileType()));
        }
        current = current.getTreeNext();
      }

    }
  }

  private static ASTNode trimWhiteSpaces(final ASTNode first, final ASTNode lastChild) {
    ASTNode current = first;
    ASTNode result = first;
    while(current != null && current != lastChild) {
      LeafElement leaf = TreeUtil.findFirstLeaf(current);
      
      if ((current == first && current.getTreeNext() != lastChild) || current.getElementType() != ElementType.WHITE_SPACE) {        
        if (leaf != null && leaf.getElementType() == ElementType.WHITE_SPACE) {
          if (leaf == result) result = leaf.getTreeNext();
          delete(leaf);
        }
      }
      leaf = TreeUtil.findLastLeaf(current);
      if (current.getElementType() != ElementType.WHITE_SPACE) {
        if (leaf != null && leaf.getElementType() == ElementType.WHITE_SPACE) {
          if (leaf == result) result = leaf.getTreeNext();
          delete(leaf);
        }
      }
      current = current.getTreeNext();
    }
    return result;
  }

  private static ASTNode findFirstValid(final List<ASTNode> treePrev) {
    for (Iterator<ASTNode> iterator = treePrev.iterator(); iterator.hasNext();) {
      ASTNode treeElement = iterator.next();
      if (treeElement.getPsi().isValid()) return treeElement;
    }
    return null;
  }

  private static List<ASTNode> getPreviousElements(final ASTNode first) {
    ASTNode current = first.getTreePrev();
    if (current == null) return null;
    final ArrayList<ASTNode> result = new ArrayList<ASTNode>();
    while (current != null) {
      result.add(current);
      current = current.getTreePrev();
    }
    return result;
  }

  private static void adjustWhiteSpaces(final ASTNode first, final ASTNode last, final boolean keepFirstIndent) {
    ASTNode current = first;
    while (current != null && current != last) {
      if (notIsSpace(current)) {
        adjustWhiteSpaceBefore(current, true, false, !keepFirstIndent || current != first, true);
      }
      current = current.getTreeNext();
    }
  }

  private static boolean notIsSpace(final ASTNode current) {
    return current.getElementType() != ElementType.WHITE_SPACE && current.getText().trim().length() > 0;
  }

  private static void adjustWhiteSpaceBefore(final ASTNode first,
                                             final boolean keepBlankLines,
                                             final boolean keepLineBreaks,
                                             final boolean changeWSBeforeFirstElement, final boolean changeLineFeedsBeforeFirstElement) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(first.getPsi().getProject());
    final PsiElement psi = first.getPsi();
    final PsiFile file = psi.getContainingFile();
    final FormattingModelBuilder builder = file.getLanguage().getFormattingModelBuilder();
    final FormattingModelBuilder elementBuilder = psi.getLanguage().getFormattingModelBuilder();
    if (builder != null && elementBuilder != null) {
      Formatter.getInstance().adjustTextRange(builder.createModel(file, settings), settings,
                                              settings.getIndentOptions(file.getFileType()),
                                              first.getTextRange(),
                                              keepBlankLines,
                                              keepLineBreaks,
                                              changeWSBeforeFirstElement,
                                              changeLineFeedsBeforeFirstElement, new MyIndentInfoStorage(file));
    }
  }


  private static String composeNewWS(final String firstText,
                                     final String secondText,
                                     final CodeStyleSettings.IndentOptions options) {
    IndentInfo first = createInfo(firstText, options);
    IndentInfo second = createInfo(secondText, options);
    final int lineFeeds = Math.max(first.getLineFeeds(), second.getLineFeeds());
    return new IndentInfo(lineFeeds,
                          0,
                          second.getSpaces()).generateNewWhiteSpace(options);
  }

  private static IndentInfo createInfo(final String text, final CodeStyleSettings.IndentOptions options) {
    int lf = 0;
    int spaces = 0;
    for (int i  = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      switch(c) {
        case ' ': spaces++; break;
        case '\t': spaces += options.TAB_SIZE; break;
        case '\n': lf++; spaces = 0; break;
      }
    }
    return new IndentInfo(lf, 0, spaces);
  }

  private static boolean containLineBreaks(final ASTNode first, final int endOffset) {
    ASTNode current = first;
    while (current != null && current.getTextRange().getStartOffset()< endOffset) {
      if (current.textContains('\n')) return true;
      current = TreeUtil.nextLeaf(current);
    }
    return false;
  }

  private static void delete(final ASTNode prevElement) {
    prevElement.getTreeParent().removeRange(prevElement, prevElement.getTreeNext());
  }

  private static ASTNode findElementAfter(final ASTNode parent, boolean canBeWhiteSpace) {
    ASTNode candidate = parent.getTreeNext();
    while (candidate !=null) {
      if ((canBeWhiteSpace || !isWS(candidate)) && candidate.getTextLength() > 0) return candidate;
      candidate =  candidate.getTreeNext();
    }
    final ASTNode treeParent = parent.getTreeParent();
    if (treeParent != null) {
      return findElementAfter(treeParent, canBeWhiteSpace);
    } else {
      return null;
    }
  }

  private static boolean isWS(final ASTNode lastChild) {
    if (lastChild == null)return false;
    return lastChild.getElementType() == ElementType.WHITE_SPACE;
  }

  public static ASTNode replaceChild(CompositeElement parent, ASTNode oldChild, ASTNode newChild) {
    if (DO_OUTPUT) {
      System.out.println("CodeEditUtil.replaceChildrenBefore\n" + parent.getPsi().getContainingFile().getText());
    }

    checkAllWhiteSpaces(parent);
    final ASTNode elementAfter = findElementAfter(oldChild, true);

    boolean changeFirstWS = newChild.textContains('\n') || oldChild.textContains('\n');

    ASTNode firstNonEmpty = findFirstNonEmpty(newChild, newChild.getTreeNext(), parent, newChild.getTreeNext());
    
    if (!canStickChildrenTogether(TreeUtil.prevLeaf(oldChild), firstNonEmpty)) {
      changeFirstWS = true;
    }

    if (changeFirstWS) {
      saveIndents(newChild);
    }
    if (elementAfter != null && !isWS(elementAfter)) {
      saveIndents(elementAfter);
    }

    parent.replaceChild(oldChild, newChild);

    final List<ASTNode> treePrev = getPreviousElements(newChild);

    adjustWhiteSpaceBefore(newChild, true, false, changeFirstWS, false);
    if (elementAfter != null && !isWS(elementAfter)) {
      adjustWhiteSpaceBefore(elementAfter, true, true, true, false);
    }

    checkAllWhiteSpaces(parent);
    if (DO_OUTPUT) {
      System.out.println("CodeEditUtil.replaceChildren\n" + parent.getPsi().getContainingFile().getText());
    }
    if (treePrev == null) {
      return (TreeElement)parent.getFirstChildNode();
    } else {
      ASTNode firstValid = findFirstValid(treePrev);
      if (firstValid == null) {
        return (TreeElement)parent.getFirstChildNode();
      } else {
        return (TreeElement)firstValid.getTreeNext();
      }
    }
  }

  private static void saveIndents(final ASTNode newChild) {
    saveIndents(newChild, newChild.getTreeNext());
  }

  public static boolean canStickChildrenTogether(final ASTNode child1, final ASTNode child2) {
    if (child1 == null || child2 == null) return true;
    if (isWS(child1) || isWS(child2)) return true;

    ASTNode token1 = TreeUtil.findLastLeaf(child1);
    ASTNode token2 = TreeUtil.findFirstLeaf(child2);

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

  private static Map<Pair<IElementType, IElementType>, Boolean> myCanStickJavaTokensMatrix = new HashMap<Pair<IElementType, IElementType>, Boolean>();

  public static boolean canStickJavaTokens(PsiJavaToken token1, PsiJavaToken token2) {
    IElementType type1 = token1.getTokenType();
    IElementType type2 = token2.getTokenType();

    Pair<IElementType, IElementType> pair = new Pair<IElementType, IElementType>(type1, type2);
    Boolean res = myCanStickJavaTokensMatrix.get(pair);
    if (res == null) {
      String text = token1.getText() + token2.getText();
      Lexer lexer = new JavaLexer(LanguageLevel.HIGHEST);
      lexer.start(text.toCharArray(), 0, text.length());
      boolean canMerge = lexer.getTokenType() == type1;
      lexer.advance();
      canMerge &= lexer.getTokenType() == type2;
      res = Boolean.valueOf(canMerge);
      myCanStickJavaTokensMatrix.put(pair, res);
    }
    return res.booleanValue();
  }

  public static void unindentSubtree(ASTNode clone, TreeElement original, CharTable table) {
    PsiManager manager = original.getManager();
    LOG.assertTrue(manager != null, "Manager should present (?)");
    LOG.assertTrue(clone.getTreeParent().getElementType() == ElementType.DUMMY_HOLDER);

    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(original).getContainingFile();
    FileType fileType = file.getFileType();
    Helper helper = new Helper(fileType, manager.getProject());
    /*
        TreeElement prevWS = helper.getPrevWhitespace(original);
        if( prevWS == null || prevWS.getText().indexOf('\n') < 0 ) return;
    */
    if (clone instanceof CompositeElement) {
      int origIndent = helper.getIndent(original);

      helper.indentSubtree(clone, origIndent, 0, table);
    }
  }

  public static ASTNode getDefaultAnchor(PsiImportList list, PsiImportStatementBase statement) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(list.getProject());
    ImportHelper importHelper = new ImportHelper(settings);
    return importHelper.getDefaultAnchor(list, statement);
  }

  private static final HashMap<String, Integer> ourModifierToOrderMap = new HashMap<String, Integer>();

  static { //TODO : options?
    ourModifierToOrderMap.put(PsiModifier.PUBLIC, new Integer(1));
    ourModifierToOrderMap.put(PsiModifier.PRIVATE, new Integer(1));
    ourModifierToOrderMap.put(PsiModifier.PROTECTED, new Integer(1));
    ourModifierToOrderMap.put(PsiModifier.NATIVE, new Integer(2));
    ourModifierToOrderMap.put(PsiModifier.STATIC, new Integer(3));
    ourModifierToOrderMap.put(PsiModifier.ABSTRACT, new Integer(3));
    ourModifierToOrderMap.put(PsiModifier.FINAL, new Integer(4));
    ourModifierToOrderMap.put(PsiModifier.SYNCHRONIZED, new Integer(5));
    ourModifierToOrderMap.put(PsiModifier.TRANSIENT, new Integer(5));
    ourModifierToOrderMap.put(PsiModifier.VOLATILE, new Integer(5));
    ourModifierToOrderMap.put(PsiModifier.STRICTFP, new Integer(6));
  }

  public static ASTNode getDefaultAnchor(PsiModifierList modifierList, PsiKeyword modifier) {
    Integer order = ourModifierToOrderMap.get(modifier.getText());
    if (order == null) return null;
    for (ASTNode child = SourceTreeToPsiMap.psiElementToTree(modifierList).getFirstChildNode();
         child != null;
         child = child.getTreeNext()) {
      if (ElementType.KEYWORD_BIT_SET.isInSet(child.getElementType())) {
        Integer order1 = ourModifierToOrderMap.get(child.getText());
        if (order1 == null) continue;
        if (order1.intValue() > order.intValue()) {
          return child;
        }
      }
    }
    return null;
  }

  public static PsiElement getDefaultAnchor(PsiClass aClass, PsiMember member) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(aClass.getProject());

    int order = getMemberOrderWeight(member, settings);
    if (order < 0) return null;

    PsiElement lastMember = null;
    for (PsiElement child = aClass.getFirstChild(); child != null; child = child.getNextSibling()) {
      int order1 = getMemberOrderWeight(child, settings);
      if (order1 < 0) continue;
      if (order1 > order) {
        if (lastMember != null) {
          PsiElement nextSibling = lastMember.getNextSibling();
          while (nextSibling instanceof PsiJavaToken &&
                 (nextSibling.getText().equals(",") || nextSibling.getText().equals(";"))) {
            nextSibling = nextSibling.getNextSibling();
          }
          return nextSibling == null ? aClass.getLBrace().getNextSibling() : nextSibling;
        }
        else {
          return aClass.getLBrace().getNextSibling();
        }
      }
      lastMember = child;
    }
    return aClass.getRBrace();
  }

  private static int getMemberOrderWeight(PsiElement member, CodeStyleSettings settings) {
    if (member instanceof PsiField) {
      return member instanceof PsiEnumConstant ? 1 : settings.FIELDS_ORDER_WEIGHT + 1;
    }
    else if (member instanceof PsiMethod) {
      return ((PsiMethod)member).isConstructor() ? settings.CONSTRUCTORS_ORDER_WEIGHT + 1 : settings.METHODS_ORDER_WEIGHT + 1;
    }
    else if (member instanceof PsiClass) {
      return settings.INNER_CLASSES_ORDER_WEIGHT + 1;
    }
    else {
      return -1;
    }
  }

  public static void normalizeSpace(Helper helper, ASTNode parent, ASTNode child1, ASTNode child2, CharTable table) {
    StringBuffer buffer = null;
    int count = 0;
    for (ASTNode child = child1 != null ? child1.getTreeNext() : parent.getFirstChildNode(); child != child2; child = child.getTreeNext()) {
      if (child instanceof CompositeElement && child.getFirstChildNode() == null) continue;

      if (Helper.isNonSpace(child)) {
        LOG.error("Whitespace expected");
      }
      if (buffer == null) {
        buffer = new StringBuffer();
      }
      buffer.append(child.getText());
      count++;
    }

    if (count > 1) {
      LeafElement newSpace =
        Factory.createSingleLeafElement(JavaTokenType.WHITE_SPACE, buffer.toString().toCharArray(), 0, buffer.length(), table, SharedImplUtil.getManagerByTree(parent));
      count = 0;
      ASTNode next;
      for (ASTNode child = child1 != null ? child1.getTreeNext() : parent.getFirstChildNode(); child != child2; child = next) {
        next = child.getTreeNext();
        if (child instanceof CompositeElement && child.getTextLength() == 0) continue;
        if (count == 0) {
          parent.replaceChild(child, newSpace);
        }
        else {
          parent.removeChild(child);
        }
        count++;
      }
    }

    if (child1 != null && child2 != null && Helper.getSpaceText(parent, child1, child2).length() == 0) {
      if (!helper.canStickChildrenTogether(child1, child2)) {
        helper.makeSpace(parent, child1, child2, " ");
      }
    }
  }

  public static String getStringWhiteSpaceBetweenTokens(ASTNode first, ASTNode second, Language language) {
    final PseudoTextBuilder pseudoTextBuilder = language.getFormatter();
    if (pseudoTextBuilder == null) {
      final LeafElement leafElement = ParseUtil.nextLeaf((TreeElement)first, null);
      if (leafElement != second) {
        return leafElement.getText();
      }
      else {
        return null;
      }
    }
    else {
      final PsiElement secondAsPsiElement = SourceTreeToPsiMap.treeElementToPsi(second);
      LOG.assertTrue(secondAsPsiElement != null);
      final PsiFile file = secondAsPsiElement.getContainingFile();
      final Project project = secondAsPsiElement.getProject();
      final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
      return getWhiteSpaceBeforeToken(second, language, true).generateNewWhiteSpace(settings.getIndentOptions(file.getFileType()));
    }

  }

  public static IndentInfo getIndentWhiteSpaceBeforeToken(final ASTNode tokenNode,
                                                          final Language language) {
    return getWhiteSpaceBeforeToken(tokenNode, chooseLanguage(tokenNode, language), false);
  }

  private static Language chooseLanguage(final ASTNode tokenNode, final Language language) {
    if (tokenNode == null) return language;
    final PsiElement secondAsPsiElement = SourceTreeToPsiMap.treeElementToPsi(tokenNode);
    if (secondAsPsiElement == null) return language;
    final PsiFile file = secondAsPsiElement.getContainingFile();
    if (file == null) return language;
    final FileType fileType = file.getFileType();
    if (!(fileType instanceof LanguageFileType)) {
      return language;
    }

    if (((LanguageFileType)fileType).getLanguage().getFormatter() == null) {
      return language;
    } else {
      return ((LanguageFileType)fileType).getLanguage();
    }

  }

  public static IndentInfo getWhiteSpaceBeforeToken(final ASTNode tokenNode,
                                                    final Language language,
                                                    final boolean mayChangeLineFeeds) {
    LOG.assertTrue(tokenNode != null);
    final PsiElement secondAsPsiElement = SourceTreeToPsiMap.treeElementToPsi(tokenNode);
    LOG.assertTrue(secondAsPsiElement != null);
    final PsiFile file = secondAsPsiElement.getContainingFile();
    final Project project = secondAsPsiElement.getProject();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    final int tokenStartOffset = tokenNode.getStartOffset();

    final boolean oldValue = settings.XML_KEEP_LINE_BREAKS;
    //settings.XML_KEEP_LINE_BREAKS = false;
    final int oldKeepBlankLines = settings.XML_KEEP_BLANK_LINES;
    settings.XML_KEEP_BLANK_LINES = 0;
    try {
      return getWhiteSpaceBeforeImpl(file, tokenStartOffset, language, project, settings, mayChangeLineFeeds);

    }
    finally {
      settings.XML_KEEP_LINE_BREAKS = oldValue;
      settings.XML_KEEP_BLANK_LINES = oldKeepBlankLines;
    }
  }

  private static IndentInfo getWhiteSpaceBeforeImpl(final PsiFile file,
                                                    final int tokenStartOffset,
                                                    final Language language,
                                                    final Project project,
                                                    final CodeStyleSettings settings,
                                                    final boolean mayChangeLineFeeds) {
    final FormattingModelBuilder builder = language.getFormattingModelBuilder();
    final PsiElement element = file.findElementAt(tokenStartOffset);

    if (builder != null && element.getLanguage().getFormattingModelBuilder() != null) {

      final TextRange textRange = element.getTextRange();
      final FormattingModel model = builder.createModel(file, settings);
      return Formatter.getInstance().getWhiteSpaceBefore(model.getDocumentModel(),
                                                         model.getRootBlock(),
                                                         settings, settings.getIndentOptions(file.getFileType()), textRange,
                                                         mayChangeLineFeeds);
    } else {
      final PseudoTextBuilder pseudoTextBuilder = language.getFormatter();
      LOG.assertTrue(pseudoTextBuilder != null);

      final PseudoText pseudoText = pseudoTextBuilder.build(project,
                                                            settings,
                                                            file);
      return GeneralCodeFormatter.getWhiteSpaceBetweenTokens(pseudoText, settings, file.getFileType(), tokenStartOffset, mayChangeLineFeeds);
    }
  }

  private static class MyIndentInfoStorage implements Formatter.IndentInfoStorage {
    private final PsiFile myFile;

    public MyIndentInfoStorage(final PsiFile file) {
      myFile = file;
    }

    public void saveIndentInfo(IndentInfo info, int startOffset) {
      myFile.findElementAt(startOffset).putUserData(INDENT_INFO_KEY, info);
    }

    public IndentInfo getIndentInfo(int startOffset) {
      return myFile.findElementAt(startOffset).getUserData(INDENT_INFO_KEY);
    }
  }
}
