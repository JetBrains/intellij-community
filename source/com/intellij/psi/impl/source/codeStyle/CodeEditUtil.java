package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.CharTable;
import com.intellij.util.containers.HashMap;
import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.codeFormatting.PseudoText;
import com.intellij.lang.ASTNode;

public class CodeEditUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeEditUtil");

  public static ASTNode addChild(CompositeElement parent, TreeElement child, TreeElement anchorBefore) {
    return addChildren(parent, child, child, anchorBefore);
  }

  public static TreeElement addChildren(CompositeElement parent, TreeElement first, ASTNode last, TreeElement anchorBefore) {
    final Project project = parent.getManager().getProject();
    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(parent).getContainingFile();
    FileType fileType = file.getFileType();

    //???
    if (first.getElementType() == ElementType.WHITE_SPACE){
      if (first == last) return null;
      first = first.getTreeNext();
    }
    if (last.getElementType() == ElementType.WHITE_SPACE){
      if (first == last) return null;
      last = last.getTreePrev();
    }

    boolean oneElement = first == last;
    boolean adjustSelf = parent.getTreeParent() != null && parent.getTextLength() == 0;

    final Helper helper = new Helper(fileType, project);
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    final CodeFormatterFacade codeLayouter = new CodeFormatterFacade(settings, helper);
    final IndentAdjusterFacade indentAdjuster = new IndentAdjusterFacade(settings, helper);
    final int oldIndent = helper.getIndent(first);

    ASTNode afterLast = last.getTreeNext();
    TreeElement next;
    for(TreeElement child = first; child != afterLast; child = next){
      next = child.getTreeNext();
      ChangeUtil.addChild(parent, child, anchorBefore);
    }

    if(fileType == StdFileTypes.XHTML) return first;
    if (adjustSelf){
      ASTNode elementBefore = Helper.shiftBackwardToNonSpace(parent.getTreePrev());
      parent = (CompositeElement)codeLayouter.processSpace(elementBefore, parent);
      parent = (CompositeElement)indentAdjuster.adjustFirstLineIndent(parent);
      ASTNode elementAfter = Helper.shiftForwardToNonSpace(parent.getTreeNext());
      elementAfter = codeLayouter.processSpace(parent, elementAfter);
      if (elementAfter != null){
        elementAfter = indentAdjuster.adjustFirstLineIndent(elementAfter);
      }
    }

    ASTNode elementBefore = Helper.shiftBackwardToNonSpace(first.getTreePrev());
    if (needSeparateLines(first) && helper.getLineBreakCount(parent, elementBefore, first) == 0){
      helper.makeVerticalSpace(parent, elementBefore, first, 0);
    }
    first = (TreeElement)codeLayouter.processSpace(elementBefore, first);// special mode?

    // restore old indent of the first line
    ASTNode prev = Helper.shiftBackwardToNonSpace(first.getTreePrev());
    String spaceText = Helper.getSpaceText(parent, prev, first);
    int index = Math.max(spaceText.lastIndexOf('\n'), spaceText.lastIndexOf('\r'));
    if (index >= 0
      || (parent.getTreeParent() == null && prev == null && first.getTextRange().getStartOffset() == spaceText.length())){
      spaceText = spaceText.substring(0, index + 1) + helper.fillIndent(oldIndent);
      first = (TreeElement)helper.makeSpace(parent, prev, first, spaceText, false);
    }

    if (oneElement){
      last = first;
    }

    ASTNode elementAfter = Helper.shiftForwardToNonSpace(last.getTreeNext());
    if (elementAfter != null && needSeparateLines(last) && helper.getLineBreakCount(parent, last, elementAfter) == 0){
      helper.makeVerticalSpace(parent, last, elementAfter, 0);
    }
    elementAfter = codeLayouter.processSpace(last, elementAfter);// special mode?
    if (elementAfter != null){
      elementAfter = indentAdjuster.adjustFirstLineIndent(elementAfter);
    }

    afterLast = last.getTreeNext();
    for(ASTNode child = first; child != afterLast; child = child.getTreeNext()){
      child = indentAdjuster.adjustNormalizeIndent(child);
      if (child.getElementType() == ElementType.CODE_BLOCK){
        PsiElement[] children = SourceTreeToPsiMap.treeElementToPsi(child).getChildren();
        for(int i = 0; i < children.length; i++){
          PsiElement element = children[i];
          ASTNode child1 = SourceTreeToPsiMap.psiElementToTree(element);
          if (Helper.isNonSpace(child1)){
            child1 = indentAdjuster.adjustIndent(child1);
          }
        }
      }
    }

    return first;
  }

  public static void removeChild(CompositeElement parent, TreeElement child) {
    removeChildren(parent, child, child);
  }

  public static void removeChildren(CompositeElement parent, TreeElement first, ASTNode last) {
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(parent);
    final PsiManager manager = parent.getManager();
    final Project project = manager.getProject();
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);

    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(parent).getContainingFile();
    FileType fileType = file.getFileType();
    Helper helper = new Helper(fileType, project);
    CodeFormatterFacade codeLayouter = new CodeFormatterFacade(settings, helper);
    IndentAdjusterFacade indentAdjuster = new IndentAdjusterFacade(settings, helper);

    ASTNode prev = first.getTreePrev();
    ASTNode next = last.getTreeNext();
    ASTNode prevNonSpace = Helper.shiftBackwardToNonSpace(prev);
    ASTNode nextNonSpace = Helper.shiftForwardToNonSpace(next);
    TreeElement spaceBefore = Helper.getSpaceElement(parent, prevNonSpace, first);
    TreeElement spaceAfter = Helper.getSpaceElement(parent, last, nextNonSpace);

    TreeElement childNext;
    for(TreeElement child = first; child != next; child = childNext){
      childNext = child.getTreeNext();
      ChangeUtil.removeChild(parent, child);
    }
    if(fileType == StdFileTypes.XHTML) return;

    if (prevNonSpace == null && nextNonSpace == null){
      if (spaceBefore != null){
        ChangeUtil.removeChild(parent, spaceBefore);
      }
      if (spaceAfter != null){
        ChangeUtil.removeChild(parent, spaceAfter);
      }
      LOG.assertTrue(parent.getTextLength() == 0);
      ASTNode elementBefore = Helper.shiftBackwardToNonSpace(parent.getTreePrev());
      ASTNode elementAfter = Helper.shiftForwardToNonSpace(parent.getTreeNext());
      normalizeSpace(helper, parent.getTreeParent(), elementBefore, elementAfter, charTableByTree);
      elementAfter = codeLayouter.processSpace(elementBefore, elementAfter);
      if (elementAfter != null){
        elementAfter = indentAdjuster.adjustFirstLineIndent(elementAfter);
      }
      return;
    }

    int breaksBefore = spaceBefore != null ? StringUtil.getLineBreakCount(spaceBefore.getText()) : 0;
    int breaksAfter = spaceAfter != null ? StringUtil.getLineBreakCount(spaceAfter.getText()) : 0;

    normalizeSpace(helper, parent, prevNonSpace, nextNonSpace, charTableByTree);

    int newBreaks = Math.max(breaksBefore, breaksAfter);
    if (newBreaks != breaksBefore + breaksAfter){
      helper.makeLineBreaks(parent, prevNonSpace, nextNonSpace, newBreaks);
    }

    nextNonSpace = codeLayouter.processSpace(prevNonSpace, nextNonSpace);
    if (nextNonSpace != null){
      nextNonSpace = indentAdjuster.adjustFirstLineIndent(nextNonSpace);
    }
  }

  public static ASTNode replaceChild(CompositeElement parent, TreeElement oldChild, TreeElement newChild) {
    final PsiManager manager = parent.getManager();
    final Project project = manager.getProject();
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(parent);
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    //CodeFormatterFacade codeLayouter = new CodeFormatterFacade(settings, helper);
    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(parent).getContainingFile();
    FileType fileType = file.getFileType();

    Helper helper = new Helper(fileType, project);
    IndentAdjusterFacade indentAdjuster = new IndentAdjusterFacade(settings, helper);

    int oldIndent = helper.getIndent(newChild);

    ChangeUtil.replaceChild(parent, oldChild, newChild);
    if(fileType == StdFileTypes.XHTML) return newChild;

    ASTNode prevNonSpace = Helper.shiftBackwardToNonSpace(newChild.getTreePrev());
    ASTNode nextNonSpace = Helper.shiftForwardToNonSpace(newChild.getTreeNext());

    if (newChild instanceof CompositeElement && newChild.getTextLength() == 0){
      normalizeSpace(helper, parent, prevNonSpace, nextNonSpace, treeCharTab);
    }
    else{
      normalizeSpace(helper, parent, prevNonSpace, newChild, treeCharTab); // just to not allow sticking tokens
      normalizeSpace(helper, parent, newChild, nextNonSpace, treeCharTab); // just to not allow sticking tokens
    }

    // restore old indent of the first line
    String newChildText = newChild.getText();
    boolean isMultiline = newChildText.indexOf('\n') >= 0 || newChildText.indexOf('\r') >= 0;
    if (isMultiline){
      String spaceText = helper.getSpaceText(parent, prevNonSpace, newChild);
      int index = Math.max(spaceText.lastIndexOf('\n'), spaceText.lastIndexOf('\r'));
      if (index >= 0
        || (parent.getTreeParent() == null && prevNonSpace == null && newChild.getTextRange().getStartOffset() == spaceText.length())
      ){
        spaceText = spaceText.substring(0, index + 1) + helper.fillIndent(oldIndent);
        newChild = (TreeElement)helper.makeSpace(parent, prevNonSpace, newChild, spaceText, false);
        //indentAdjuster.adjustIndent(newChild);
      }
    }
    indentAdjuster.adjustNormalizeIndent(newChild);

    return newChild;
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
    if( clone instanceof CompositeElement) {
      CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(manager.getProject());

      int origIndent = helper.getIndent(original);

      helper.indentSubtree((CompositeElement) clone, origIndent, 0, table);
    }
  }

  public static TreeElement getDefaultAnchor(PsiImportList list, PsiImportStatementBase statement) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(list.getProject());
    ImportHelper importHelper = new ImportHelper(settings);
    return importHelper.getDefaultAnchor(list, statement);
  }

  private static final HashMap ourModifierToOrderMap = new HashMap();

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
    Integer order = (Integer)ourModifierToOrderMap.get(modifier.getText());
    if (order == null) return null;
    for(ASTNode child = SourceTreeToPsiMap.psiElementToTree(modifierList).getFirstChildNode();
        child != null;
        child = child.getTreeNext()){
      if (ElementType.KEYWORD_BIT_SET.isInSet(child.getElementType())){
        Integer order1 = (Integer)ourModifierToOrderMap.get(child.getText());
        if (order1 == null) continue;
        if (order1.intValue() > order.intValue()){
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
    if (member instanceof PsiField){
      return member instanceof PsiEnumConstant ? 1 : settings.FIELDS_ORDER_WEIGHT + 1;
    }
    else if (member instanceof PsiMethod){
      return ((PsiMethod)member).isConstructor() ? settings.CONSTRUCTORS_ORDER_WEIGHT + 1: settings.METHODS_ORDER_WEIGHT + 1;
    }
    else if (member instanceof PsiClass){
      return settings.INNER_CLASSES_ORDER_WEIGHT + 1;
    }
    else{
      return -1;
    }
  }

  private static boolean needSeparateLines(ASTNode element) {
    if (ElementType.STATEMENT_BIT_SET.isInSet(element.getElementType())){
      return true;
    }
    else if (element.getElementType() == ElementType.FIELD){
      return true;
    }
    else if (element.getElementType() == ElementType.METHOD){
      return true;
    }
    else if (element.getElementType() == ElementType.CLASS){
      return true;
    }
    else if (element.getElementType() == ElementType.CLASS_INITIALIZER){
      return true;
    }
    else{
      return false;
    }
  }

  /*
  private static void normalizeSpaces(CompositeElement parent){
    Element child1 = parent.firstChild;
    if (child1 == null) return;
    while(true){
      Element child2;
      for(child2 = child1.next; child2 != null; child2 = child2.next){
        if (child2.type == JavaTokenType.XML_WHITE_SPACE) continue;
        if (child2 instanceof CompositeElement && ((CompositeElement)child2).firstChild == null) continue;
        break;
      }
      if (child2 == null) break;
      normalizeSpace(parent, child1, child2);
      child1 = child2;
    }
  }
  */

  public static void normalizeSpace(Helper helper, CompositeElement parent, ASTNode child1, ASTNode child2, CharTable table) {
    StringBuffer buffer = null;
    int count = 0;
    for(ASTNode child = child1 != null ? child1.getTreeNext() : parent.getFirstChildNode(); child != child2; child = child.getTreeNext()){
      if (child instanceof CompositeElement && child.getFirstChildNode() == null) continue;

      if (Helper.isNonSpace(child)) {
        LOG.error("Whitespace expected");
      }
      if (buffer == null){
        buffer = new StringBuffer();
      }
      buffer.append(child.getText());
      count++;
    }

    if (count > 1){
      LeafElement newSpace = Factory.createSingleLeafElement(JavaTokenType.WHITE_SPACE, buffer.toString().toCharArray(), 0, buffer.length(), table, null);
      count = 0;
      ASTNode next;
      for(ASTNode child = child1 != null ? child1.getTreeNext() : parent.getFirstChildNode(); child != child2; child = next){
        next = child.getTreeNext();
        if (child instanceof CompositeElement && child.getTextLength() == 0) continue;
        if (count == 0){
          ChangeUtil.replaceChild(parent, (TreeElement)child, newSpace);
        }
        else{
          ChangeUtil.removeChild(parent, (TreeElement)child);
        }
        count++;
      }
    }

    if (child1 != null && child2 != null && Helper.getSpaceText(parent, child1, child2).length() == 0){
      if (!helper.canStickChildrenTogether(child1, child2)){
        helper.makeSpace(parent, child1, child2, " ");
      }
    }
  }

  public static String getWhiteSpaceBetweenTokens(ASTNode first, ASTNode second) {
    final PsiElement firstAsPsiElement = SourceTreeToPsiMap.treeElementToPsi(first);
    LOG.assertTrue(firstAsPsiElement != null);
    final PsiFile file = firstAsPsiElement.getContainingFile();
    final FileType fileType = file.getVirtualFile().getFileType();
    final PseudoTextBuilder pseudoTextBuilder = fileType.getPseudoTextBuilder();
    LOG.assertTrue(pseudoTextBuilder != null);
    final Project project = firstAsPsiElement.getProject();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    final int startOffset = first.getStartOffset();
    final int endOffset = second.getStartOffset();

    final boolean oldValue = settings.XML_KEEP_LINE_BREAKS;
    settings.XML_KEEP_LINE_BREAKS = false;

    try {
      final PseudoText pseudoText = pseudoTextBuilder.build(project,
                                                            settings,
                                                            file);

      return GeneralCodeFormatter.getWhiteSpaceBetweenTokens(pseudoText, settings, fileType, startOffset, endOffset);
    }
    finally {
      settings.XML_KEEP_LINE_BREAKS = oldValue;
    }
  }
}
