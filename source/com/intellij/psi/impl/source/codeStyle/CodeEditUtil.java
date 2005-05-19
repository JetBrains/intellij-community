package com.intellij.psi.impl.source.codeStyle;

import com.intellij.codeFormatting.PseudoText;
import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.newCodeFormatting.Formatter;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.newCodeFormatting.IndentInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.CharTable;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CodeEditUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeEditUtil");
  public static final Key<IndentInfo> INDENT_INFO_KEY = new Key<IndentInfo>("IndentInfo");

  public static ASTNode addChild(CompositeElement parent, ASTNode child, ASTNode anchorBefore) {
    return addChildren(parent, child, child, anchorBefore);
  }

  public static TreeElement addChildren(CompositeElement parent, ASTNode first, ASTNode last, ASTNode anchorBefore) {
    
    checkAllWhiteSpaces(parent);
    
    ASTNode lastChild = last != null ? last.getTreeNext() : null;
    first = trimWhiteSpaces(first, lastChild);

    if (first == null) {
      return null;
    }

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

    parent.addChildren(first, lastChild, anchorBefore);
    final List<ASTNode> treePrev = getPreviousElements(first);
    adjustWhiteSpaces(first, anchorBefore);
    if (nextElement != null) {
      adjustWhiteSpaces(nextElement, nextElement.getTreeNext());
    }
    System.out.println("CodeEditUtil.addChildren\n"+ parent.getPsi().getContainingFile().getText());
    checkAllWhiteSpaces(parent);
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

  private static void checkAllWhiteSpaces(final ASTNode parent) {
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
    checkAllWhiteSpaces(parent);
    boolean doNotAdjust = first == parent.getFirstChildNode(); 
    ASTNode lastChild = last == null ? null : last.getTreeNext();
    ASTNode prevElement = TreeUtil.prevLeaf(first);
    ASTNode nextElement = findElementAfter(last == null ? parent : last, false);
    if (nextElement != null) {
      saveIndents(nextElement);
    }
    
    boolean adjustWSBefore = containLineBreaks(first, lastChild);
    
    if (!isWS(prevElement) || (prevElement != null && !prevElement.textContains('\n'))) {
      adjustWSBefore = true;
    }
    
    parent.removeRange(first, lastChild);

    final PsiFile file = parent.getPsi().getContainingFile();      
    final CodeStyleSettings.IndentOptions options = CodeStyleSettingsManager.getSettings(file.getProject())
      .getIndentOptions(file.getFileType());
    
    if (nextElement != null) {
      
      ASTNode elementBeforeNext = TreeUtil.prevLeaf(nextElement);
      
      if (isWS(prevElement) && isWS(elementBeforeNext) && prevElement != elementBeforeNext) {
        if (!elementBeforeNext.textContains('\n') && prevElement.textContains('\n')) { 
          /*
              void foo1(){}
              static void foo2(){} 
              remove static
          */
          delete(elementBeforeNext);
        } else {
          String text = composeNewWS(prevElement.getText(), elementBeforeNext.getText(), options);
          delete(prevElement);
          if (!text.equals(elementBeforeNext.getText())){
            final CharTable charTable = SharedImplUtil.findCharTableByTree(elementBeforeNext);          
            LeafElement newWhiteSpace = Factory.createSingleLeafElement(elementBeforeNext.getElementType(), 
                                                                        text.toCharArray(), 0, text.length(),
                                                                        charTable, 
                                                                        SharedImplUtil.getManagerByTree(elementBeforeNext));
            
            elementBeforeNext.getTreeParent().replaceChild(elementBeforeNext, 
                                                           newWhiteSpace);
          }
          
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
      
      
      final boolean keepFormatting = parent.getFirstChildNode() != null;
      if (!keepFormatting) {
        doNotAdjust = false;
      }

      if (adjustWSBefore) {
        adjustWhiteSpaceBefore(nextElement, keepFormatting, keepFormatting, true, !doNotAdjust);
      }
      
    } else {
      final ASTNode fileNode = SourceTreeToPsiMap.psiElementToTree(parent.getPsi().getContainingFile());
      ASTNode lastLeaf = TreeUtil.findLastLeaf(fileNode);
      if (isWS(lastLeaf)) {
        delete(lastLeaf);
      }
    }
    System.out.println("CodeEditUtil.removeChildrenNew\n" + parent.getPsi().getContainingFile().getText());
    checkAllWhiteSpaces(parent);
    //removeChildrenOld(parent,first, last);
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
    if (element.getTextRange().getEndOffset() == element.getPsi().getContainingFile().getTextLength()) 
      return element.getTreeNext() == null;    
    return false;
  }

  private static boolean hasNonEmptyPrev(ASTNode element) {
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
    if (isWS(element) && treeParent.getElementType() == ElementType.XML_TEXT) return false;
    if (!hasNonEmptyPrev(element)) return true;
    if (treeParent.getPsi().getUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY) != null) return false;
    if (treeParent.getElementType() == ElementType.XML_PROLOG) return false;
    return !hasNonEmptyNext(element);
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

  private static ASTNode trimWhiteSpaces(ASTNode first, final ASTNode lastChild) {
    while(first != null && first != lastChild) {
      if (first.getElementType() != ElementType.WHITE_SPACE) return first;
      first = first.getTreeNext();
    }
    return null;
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

  private static void adjustWhiteSpaces(final ASTNode first, final ASTNode last) {
    ASTNode current = first;
    while (current != null && current != last) {
      if (notIsSpace(current)) {
        adjustWhiteSpaceBefore(current, true, false, true, true);
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
    checkAllWhiteSpaces(parent);
    final ASTNode elementAfter = findElementAfter(oldChild, true);
    
    boolean changeFirstWS = newChild.textContains('\n') || oldChild.textContains('\n');
    
    if (!canStickChildrenTogether(TreeUtil.prevLeaf(oldChild), newChild)) {
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
    System.out.println("CodeEditUtil.replaceChild\n"+ parent.getPsi().getContainingFile().getText());
    checkAllWhiteSpaces(parent);
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

  private static boolean canStickChildrenTogether(final ASTNode child1, final ASTNode child2) {
    if (child1 == null || child2 == null) return true;
    if (isWS(child1) || isWS(child2)) return true;
    final PsiFile file = child1.getPsi().getContainingFile();
    
    return new Helper(file.getFileType(), file.getProject()).canStickChildrenTogether(child1, 
                                                                                      child2);    
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
