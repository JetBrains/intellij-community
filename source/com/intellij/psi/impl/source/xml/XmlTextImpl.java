package com.intellij.psi.impl.source.xml;

import com.intellij.psi.xml.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.xml.aspect.XmlTextChanged;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.CharTable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xml.util.XmlTagTextUtil;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomTransaction;
import com.intellij.pom.event.PomModelEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class XmlTextImpl extends XmlElementImpl implements XmlText{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTextImpl");
  private String myDisplayText = null;

  public XmlTextImpl() {
    super(XML_TEXT);
  }

  public String toString() {
    return "XmlText";
  }

  public XmlText split(int displayIndex) {
    final int phyIndex = displayToPhysical(displayIndex);
    final String text = getText();
    final int phyTextLength = text.length();
    if (phyIndex == 0 || phyIndex == phyTextLength) {
      return this;
    }
    try {
      setValue(text.substring(0, phyIndex));
      final XmlText element = getManager().getElementFactory().createDisplayText(text.substring(phyIndex));
      getParent().addAfter(element, this);
      return element;
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  private int[] myGaps = null;

  public String getValue() {
    if(myDisplayText != null) return myDisplayText;
    StringBuffer buffer = new StringBuffer();
    TreeElement child = firstChild;
    final List<Integer> gaps = new ArrayList<Integer>();
    while(child != null){
      final int start = buffer.length();
      IElementType elementType = child.getElementType();
      if(elementType == XmlElementType.XML_CDATA){
        final CompositeElement cdata = (CompositeElement)child;
        child = cdata.firstChild;
      }
      else if(elementType == XmlTokenType.XML_CHAR_ENTITY_REF){
        buffer.append(getChar(child.getText()));
      }
      else if(elementType == XmlTokenType.XML_WHITE_SPACE
              || elementType == XmlTokenType.XML_DATA_CHARACTERS
              || elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN){
        buffer.append(child.getText());
      }
      int end = buffer.length();
      int originalLength = child.getTextLength();
      if(end - start != originalLength){
        gaps.add(new Integer(start));
        gaps.add(new Integer(originalLength - (end - start)));
      }
      final TreeElement next = child.getTreeNext();
      if(next == null && child.getTreeParent().getElementType() == XmlElementType.XML_CDATA)
        child = child.getTreeParent().getTreeNext();
      else child = next;
    }
    myGaps = new int[gaps.size()];
    int index = 0;
    final Iterator<Integer> iterator = gaps.iterator();
    while (iterator.hasNext()) {
      final Integer integer = iterator.next();
      myGaps[index++] = integer.intValue();
    }

    return myDisplayText = buffer.toString();
  }

  private char getChar(String text) {
    //LOG.assertTrue(text.startsWith("&#") && text.endsWith(";"));
    if(text.charAt(1) != '#'){
      text = text.substring(1, text.length() - 1);
      return XmlTagTextUtil.getCharacterByEntityName(text).charValue();
    }
    text = text.substring(2, text.length() - 1);
    int code;
    if (text.startsWith("x")) {
      text = text.substring(1);
      code = Integer.parseInt(text, 16);
    }
    else {
      code = Integer.parseInt(text);
    }
    return (char) code;
  }

  public int physicalToDisplay(int offset) {
    getValue();
    if(myGaps.length == 0) return offset;
    int gapIndex = 0;
    int displayIndex = 0;
    int physicalOffset = 0;

    while(physicalOffset < offset){
      if(displayIndex == getGapStartOffset(gapIndex)){
        final int gap = getGap(gapIndex);
        physicalOffset += gap;
        gapIndex++;
      }
      else {
        physicalOffset++;
        displayIndex++;
      }
    }
    return displayIndex;
  }

  public int displayToPhysical(int displayIndex) {
    getValue();
    if(myGaps.length == 0) return displayIndex;
    int gapIndex = 0;
    int displayOffset = 0;
    int physicalOffset = 0;
    while(displayOffset <= displayIndex){
      if(displayOffset > getGapStartOffset(gapIndex)){
        final int gap = getGap(gapIndex);
        physicalOffset += gap;
        gapIndex++;
      }
      else if(displayOffset < displayIndex){
        physicalOffset++;
        displayOffset++;
      }
      else displayOffset++;
    }
    return physicalOffset;
  }

  public void setValue(String s) throws IncorrectOperationException{
    final XmlTextImpl element = (XmlTextImpl)getManager().getElementFactory().createDisplayText(s);
    replace(element);
  }

  public XmlElement insertAtOffset(final XmlElement element, int physicalOffset) throws IncorrectOperationException{
    final PsiElement elementAt = findElementAt(physicalOffset);
    final int localOffset;
    if(elementAt != null)
      localOffset = physicalOffset - elementAt.getStartOffsetInParent();
    else
      localOffset = element.getTextLength();
    final PomModel model = getManager().getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    final TreeElement[] retHolder = new TreeElement[1];
    final TreeElement insertedElement = SourceTreeToPsiMap.psiElementToTree(element);
    final String oldText = getText();
    final TreeElement second;
    if(elementAt != null){
      final TreeElement treeElement = SourceTreeToPsiMap.psiElementToTree(elementAt);
      second = split((LeafElement)treeElement, localOffset);
    }
    else second = null;


    if(element instanceof XmlTagChild){
      final XmlElement parent = getParent();
      if(second != null){
        final XmlText xmlText = getManager().getElementFactory().createTagFromText("<tag> </tag>").getValue().getTextElements()[0];
        final CompositeElement compositeElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(xmlText);
        TreeUtil.removeRange(compositeElement.firstChild, null);
        model.runTransaction(new PomTransaction() {
          public PomModelEvent run(){
            TreeElement current = second;
            while(current != null){
              final TreeElement next = current.getTreeNext();
              ChangeUtil.removeChild(XmlTextImpl.this, current);
              ChangeUtil.addChild(compositeElement, current, null);
              current = next;
            }
            return XmlTextChanged.createXmlTextChanged(model, XmlTextImpl.this, oldText);
          }
        }, aspect);

        XmlElement xmlElement = (XmlElement)parent.addAfter(element, this);
        parent.addAfter(xmlText, xmlElement);
        return xmlElement;
      }
      else{
        return (XmlElement)parent.addAfter(element, this);
      }
    }
    else{
      model.runTransaction(new PomTransaction() {
        public PomModelEvent run() {
          retHolder[0] = addInternal(insertedElement, insertedElement, retHolder[0], Boolean.TRUE);
          return XmlTextChanged.createXmlTextChanged(model, XmlTextImpl.this, oldText);
        }
      }, aspect);
    }
    return (XmlElement)SourceTreeToPsiMap.treeElementToPsi(retHolder[0]);
  }

//  public XmlElement insertAtOffset(final XmlElement element, int physicalOffset) throws IncorrectOperationException{
//    final PomModel model = getManager().getProject().getModel();
//    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
//    final int displayOffset = physicalToDisplay(physicalOffset);
//    final String value = getValue();
//    final String part1, part2;
//    if(element instanceof XmlText){
//      part1 = value.substring(0, displayOffset) + ((XmlText) element).getValue() + value.substring(displayOffset);
//      part2 = null;
//    }
//    else{
//      part1 = value.substring(0, displayOffset);
//      part2 = value.substring(displayOffset);
//    }
//    if(part2 != null){
//      if(part1.length() > 0){
//        setValue(part1);
//        final PsiElement elementCopy = getParent().addAfter(element, this);
//        if(part2.length() > 0){
//          final FileElement treeElement = new DummyHolder(null, null, SharedImplUtil.findCharTableByTree(this)).getTreeElement();
//          final XmlTextImpl newText = new XmlTextImpl();
//          TreeUtil.addChildren(treeElement, newText);
//          TreeUtil.addChildren(newText, createElementsByDisplayText(part2, getPolicy()));
//          getParent().addAfter(newText, elementCopy);
//        }
//      }
//      else {
//        getParent().addBefore(element, this);
//      }
//    }
//    else{
//      setValue(part1);
//    }
//      return this;
//  }

  private int getPolicy() {
    return CDATA_ON_TEXT;
  }

  private static LeafElement split(LeafElement element, int offset) throws IncorrectOperationException{
    final CharTable table = SharedImplUtil.findCharTableByTree(element);
    int textLength = element.getTextLength(table);
    if(textLength < offset) throw new ArrayIndexOutOfBoundsException(offset);
    if(offset == 0 || textLength == offset) return element;
    if(element.getElementType() != XmlTokenType.XML_DATA_CHARACTERS
    && element.getElementType() != XmlTokenType.XML_WHITE_SPACE)
      throw new IncorrectOperationException("Element " + element.getElementType() + " can not be split!");
    final char[] buffer = new char[textLength];
    element.copyTo(buffer, 0);
    final LeafElement firstPart = Factory.createSingleLeafElement(element.getElementType(), buffer, 0, offset, table, null);
    final LeafElement secondPart = Factory.createSingleLeafElement(element.getElementType(), buffer, offset, buffer.length, table, null);
    final CompositeElement parent = element.getTreeParent();
    ChangeUtil.replaceChild(parent, element, firstPart);
    ChangeUtil.addChild(parent, secondPart, firstPart.getTreeNext());
    return secondPart;
  }

  public void insertText(String text, int displayOffset) throws IncorrectOperationException{
    final XmlText displayText = getManager().getElementFactory().createDisplayText(text);
    final int phyOffset = displayToPhysical(displayOffset);
    insertAtOffset(displayText, phyOffset);
  }

  public void removeText(int displayStart, int displayEnd) {
    final int start = displayToPhysical(displayStart);
    final int end = displayToPhysical(displayEnd);
    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    final CharTable table = SharedImplUtil.findCharTableByTree(this);
    if(start == end) return;

    try {
      model.runTransaction(new PomTransaction() {
        public PomModelEvent run() throws IncorrectOperationException {
          final String oldText = getText();

          final LeafElement firstAffectedLeaf = findLeafElementAt(start);
          final LeafElement lastAffectedLeaf;
          final int lastAffectedLeafOffset;
          final int startOffsetInStartToken = start - firstAffectedLeaf.getStartOffsetInParent();
          {
            // remove elements inside the affected area
            LeafElement current = firstAffectedLeaf;
            int endOffset = current.getStartOffsetInParent();

            while(current != null && (endOffset += current.getTextLength(table)) <= end){
              final LeafElement toDelete = current;
              current = (LeafElement)current.getTreeNext();
              if(toDelete != firstAffectedLeaf) ChangeUtil.removeChild(XmlTextImpl.this, toDelete);
            }
            lastAffectedLeaf = current;
            lastAffectedLeafOffset = endOffset - (current != null ? current.getTextLength(table) : 0);
          }

          LeafElement tokenToChange;
          int deletedAreaStartOffset = 0;
          int deletedAreaEndOffset = 0;
          if(lastAffectedLeafOffset < end && lastAffectedLeaf != firstAffectedLeaf){
            // merging tokens
            final LeafElement merged = mergeElements(firstAffectedLeaf, lastAffectedLeaf, table);
            if(merged == null){
              ChangeUtil.removeChild(XmlTextImpl.this, split(firstAffectedLeaf, startOffsetInStartToken));
              ChangeUtil.removeChild(XmlTextImpl.this, split(lastAffectedLeaf, end - lastAffectedLeafOffset).getTreePrev());
            }
            else{
              ChangeUtil.replaceAll(new LeafElement[]{firstAffectedLeaf, lastAffectedLeaf}, merged);
              deletedAreaStartOffset = startOffsetInStartToken;
              deletedAreaEndOffset = end - lastAffectedLeafOffset + firstAffectedLeaf.getTextLength(table) - startOffsetInStartToken;
            }
            tokenToChange = merged;
          }
          else{
            // replacing first token
            tokenToChange = firstAffectedLeaf;
            final int textLength = firstAffectedLeaf.getTextLength(table);
            deletedAreaStartOffset = startOffsetInStartToken;
            deletedAreaEndOffset = Math.min(end - firstAffectedLeaf.getStartOffsetInParent(), textLength);
          }

          if(tokenToChange != null){
            final int textLength = tokenToChange.getTextLength(table);
            if(deletedAreaStartOffset > 0 || deletedAreaEndOffset < textLength) {
              String text = tokenToChange.getText(table);
              text = text.substring(0, deletedAreaStartOffset) + text.substring(deletedAreaEndOffset);
              final LeafElement newLeaf = Factory.createSingleLeafElement(firstAffectedLeaf.getElementType(),
                                                                          text.toCharArray(), 0, text.length(), table, null);
              ChangeUtil.replaceChild(XmlTextImpl.this, tokenToChange, newLeaf);
            }
            else{
              final TreeElement treeNext = tokenToChange.getTreeNext();
              final TreeElement treePrev = tokenToChange.getTreePrev();
              final LeafElement merged = mergeElements((LeafElement)treePrev, (LeafElement)treeNext, table);
              ChangeUtil.removeChild(XmlTextImpl.this, tokenToChange);
              if(merged != null) ChangeUtil.replaceAll(new LeafElement[]{(LeafElement)treePrev, (LeafElement)treeNext}, merged);
            }
          }

          return XmlTextChanged.createXmlTextChanged(model, XmlTextImpl.this, oldText);
        }
      }, aspect);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public XmlElement getParent() {
    return (XmlElement)super.getParent();
  }

  private int getGapStartOffset(int gapIndex){
    int index = gapIndex * 2;
    return index < myGaps.length ? myGaps[index] : Integer.MAX_VALUE;
  }

  private int getGap(int gapIndex){
    return myGaps[gapIndex * 2 + 1];
  }

  public XmlTag getParentTag() {
    final XmlElement parent = getParent();
    if(parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    PsiElement nextSibling = getNextSibling();
    if(nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    PsiElement prevSibling = getPrevSibling();
    if(prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean beforeB) {
    //ChameleonTransforming.transformChildren(this);
    TreeElement firstAppended = null;
    boolean before = beforeB != null ? beforeB.booleanValue() : true;
    try{
      do {
        if (firstAppended == null) {
          firstAppended = addInternal(first, anchor, before);
          anchor = firstAppended;
        }
        else anchor = addInternal(first, anchor, false);
      }
      while (first != last && (first = first.getTreeNext()) != null);
    }
    catch(IncorrectOperationException ioe){}
    return firstAppended;
  }

  public TreeElement addInternal(final TreeElement child, final TreeElement anchor, final boolean before) throws IncorrectOperationException{
    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    final TreeElement[] retHolder = new TreeElement[1];
    if (child.getElementType() == XmlElementType.XML_TEXT) {
      if(child.getTextLength() == 0) return this;
      final XmlTextImpl text = (XmlTextImpl)child;
      model.runTransaction(new PomTransaction() {
        public PomModelEvent run(){
          final String oldText = getText();
          TreeElement childBefore = anchor != null ? (before ? anchor.getTreePrev() : anchor) : lastChild;
          if(childBefore != null && childBefore.getElementType() == text.firstChild.getElementType()){
            final LeafElement newText = mergeElements((LeafElement)childBefore, (LeafElement)text.firstChild, SharedImplUtil.findCharTableByTree(XmlTextImpl.this));
            if(newText != null){
              replaceChildInternal(childBefore, newText);
              if(text.lastChild != text.firstChild){
                addChildren(XmlTextImpl.this, text.firstChild.getTreeNext(), null, anchor, before);
              }
            }
            else addChildren(XmlTextImpl.this, text.firstChild, null, anchor, before);
          }
          else{
            TreeElement childAfter = anchor != null ? (before ? anchor : anchor.getTreeNext()) : lastChild;
            if(childAfter != null && childAfter.getElementType() == text.firstChild.getElementType()){
              final LeafElement newText = mergeElements((LeafElement)text.firstChild, (LeafElement)childAfter, SharedImplUtil.findCharTableByTree(XmlTextImpl.this));
              if(newText != null){
                replaceChildInternal(childAfter, newText);
                if(text.lastChild != text.firstChild){
                  addChildren(XmlTextImpl.this, text.firstChild.getTreeNext(), null, anchor, before);
                }
              }
              else addChildren(XmlTextImpl.this, text.firstChild, null, anchor, before);
            }

            addChildren(XmlTextImpl.this, text.firstChild, null, anchor, before);
          }

          retHolder[0] = XmlTextImpl.this;
          return XmlTextChanged.createXmlTextChanged(model, XmlTextImpl.this, oldText);
        }
      }, aspect);
    }
    else{
      model.runTransaction(new PomTransaction() {
        public PomModelEvent run() {
          final String oldText = getText();
          final TreeElement treeElement = addChildren(XmlTextImpl.this, child, child.getTreeNext(), anchor, before);
          retHolder[0] = treeElement;
          return XmlTextChanged.createXmlTextChanged(model, XmlTextImpl.this, oldText);
        }
      }, aspect);
    }
    return retHolder[0];
  }

  private LeafElement mergeElements(final LeafElement one, LeafElement two, CharTable table) {
    if(one == null || two == null || one.getElementType() != two.getElementType()) return null;
    if(one.getElementType() == XmlTokenType.XML_DATA_CHARACTERS){
      final char[] buffer = new char[one.getTextLength() + two.getTextLength()];
      two.copyTo(buffer, one.copyTo(buffer, 0));
      return Factory.createSingleLeafElement(one.getElementType(), buffer, 0, buffer.length, table, null);
    }
    else if(one.getElementType() == XmlTokenType.XML_WHITE_SPACE){
      return (LeafElement)ChangeUtil.copyElement(two, SharedImplUtil.findCharTableByTree(this));
      //final LeafElement prevLeaf = ParseUtil.prevLeaf(one, null);
      //final LeafElement nextLeaf = ParseUtil.nextLeaf(two, null);
      //final PsiFile file = getContainingFile();
      //final FileType fileType = file.getFileType();
      //
      //final Helper helper = new Helper(fileType, getManager().getProject());
      //helper.getPrevWhitespace(nextLeaf);
    }
    return null;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlText(this);
  }

  public void clearCaches() {
    super.clearCaches();
    myDisplayText = null;
    myGaps = null;
  }

  private static TreeElement addChildren(final XmlTextImpl xmlText,
                   final TreeElement firstChild,
                   final TreeElement lastChild,
                   final TreeElement anchor,
                   final boolean isBefore) {
    if(isBefore){
      ChangeUtil.addChildren(xmlText, firstChild, lastChild, anchor);
    }
    else{
      ChangeUtil.addChildren(xmlText, firstChild, lastChild, anchor.getTreeNext());
    }
    return firstChild;
  }

  public static final int CDATA_ON_TEXT = 0;
  public static final int ENCODE_SYMS = 1;

  public static TreeElement createElementsByDisplayText(String displayText, int policy){
    if(!toCode(displayText))
      return Factory.createSingleLeafElement(
          XmlTokenType.XML_DATA_CHARACTERS,
          displayText.toCharArray(),
          0,
          displayText.length(),
          null, null);
    final FileElement dummyParent = new DummyHolder(null, null).getTreeElement();
    if(policy == CDATA_ON_TEXT) {
      TreeUtil.addChildren(
          dummyParent,
          Factory.createLeafElement(
              XmlTokenType.XML_CDATA_START,
              "<![CDATA[".toCharArray(),
              0, 9, -1,
              dummyParent.getCharTable()));
      TreeUtil.addChildren(
          dummyParent,
          Factory.createLeafElement(
              XmlTokenType.XML_DATA_CHARACTERS,
              displayText.toCharArray(),
              0, displayText.length(), -1,
              dummyParent.getCharTable()));
      TreeUtil.addChildren(
          dummyParent,
          Factory.createLeafElement(
              XmlTokenType.XML_CDATA_END,
              "]]>".toCharArray(),
              0, 3, -1,
              dummyParent.getCharTable()));
    }
    else if(policy == ENCODE_SYMS){
      int sectionStartOffset = 0;
      int offset = 0;
      while(offset < displayText.length()){
        if(toCode(displayText.charAt(offset))){
          final String plainSection = displayText.substring(sectionStartOffset, offset);
          if(plainSection.length() > 0)
            TreeUtil.addChildren(
                dummyParent,
                Factory.createLeafElement(
                    XmlTokenType.XML_DATA_CHARACTERS,
                    plainSection.toCharArray(),
                    0, plainSection.length(), -1,
                    dummyParent.getCharTable()));
          TreeUtil.addChildren(dummyParent, createCharEntity(displayText.charAt(offset), dummyParent.getCharTable()));
        }
      }
    }

    return dummyParent.firstChild;
  }

  private static TreeElement createCharEntity(char ch, CharTable charTable) {
    switch(ch){
      case '<':
        return Factory.createLeafElement(
            XmlTokenType.XML_CHAR_ENTITY_REF,
            "&lt;".toCharArray(),
            0, 4, -1,
            charTable);
      case '>':
        return Factory.createLeafElement(
            XmlTokenType.XML_CHAR_ENTITY_REF,
            "&gt;".toCharArray(),
            0, 4, -1,
            charTable);
      case '&':
        return Factory.createLeafElement(
            XmlTokenType.XML_CHAR_ENTITY_REF,
            "&amp;".toCharArray(),
            0, 5, -1,
            charTable);
      default:
        final String charEncoding = "&#" + (int) ch + ";";
        return Factory.createLeafElement(
            XmlTokenType.XML_CHAR_ENTITY_REF,
            charEncoding.toCharArray(),
            0, charEncoding.length(), -1,
            charTable);
    }
  }

  public static final boolean toCode(String str){
    for(int i = 0; i < str.length(); i++){
      if(toCode(str.charAt(i))) return true;
    }
    return false;
  }

  public static final boolean toCode(char ch) {
    return "<&>".indexOf(ch) >= 0;
  }
}
