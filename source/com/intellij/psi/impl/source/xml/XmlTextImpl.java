package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.XmlTextChangedImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlTagTextUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class XmlTextImpl extends XmlElementImpl implements XmlText {
  private String myDisplayText = null;

  public XmlTextImpl() {
    super(XML_TEXT);
  }

  public String toString() {
    return "XmlText";
  }

  public XmlText split(int displayIndex) {
    try {
      return ((XmlTagImpl)getParentTag()).splitText(this, displayIndex);
    }
    catch (IncorrectOperationException e) {}
    return null;
  }

  private int[] myGapDisplayStarts = null;
  private int[] myGapPhysicalStarts = null;

  public String getValue() {
    if (myDisplayText != null) return myDisplayText;
    StringBuffer buffer = new StringBuffer();
    ASTNode child = getFirstChildNode();
    final List<Integer> gapsStarts = new ArrayList<Integer>();
    final List<Integer> gapsShifts = new ArrayList<Integer>();
    while (child != null) {
      final int start = buffer.length();
      IElementType elementType = child.getElementType();
      if (elementType == XmlElementType.XML_CDATA) {
        final ASTNode cdata = child;
        child = cdata.getFirstChildNode();
      }
      else if (elementType == XmlTokenType.XML_CHAR_ENTITY_REF) {
        buffer.append(getCharFromEntityRef(child.getText()));
      }
      else if (elementType == XmlTokenType.XML_WHITE_SPACE
               || elementType == XmlTokenType.XML_DATA_CHARACTERS
               || elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        buffer.append(child.getText());
      }
      else if (elementType == JavaElementType.ERROR_ELEMENT) {
        buffer.append(child.getText());
      }

      int end = buffer.length();
      int originalLength = child.getTextLength();
      if (end - start != originalLength) {
        gapsStarts.add(new Integer(start));
        gapsShifts.add(new Integer(originalLength - (end - start)));
      }
      final ASTNode next = child.getTreeNext();
      if (next == null && child.getTreeParent().getElementType() == XmlElementType.XML_CDATA) {
        child = child.getTreeParent().getTreeNext();
      }
      else {
        child = next;
      }
    }
    myGapDisplayStarts = new int[gapsShifts.size()];
    myGapPhysicalStarts = new int[gapsShifts.size()];
    int index = 0;
    final Iterator<Integer> startsIterator = gapsStarts.iterator();
    final Iterator<Integer> shiftsIterator = gapsShifts.iterator();
    int currentGapsSum = 0;
    while (startsIterator.hasNext()) {
      final Integer integer = shiftsIterator.next();
      currentGapsSum += integer.intValue();
      myGapDisplayStarts[index] = startsIterator.next().intValue();
      myGapPhysicalStarts[index] = myGapDisplayStarts[index] + currentGapsSum;
      index++;
    }

    return myDisplayText = buffer.toString();
  }

  private static char getCharFromEntityRef(@NonNls String text) {
    //LOG.assertTrue(text.startsWith("&#") && text.endsWith(";"));
    if (text.charAt(1) != '#') {
      text = text.substring(1, text.length() - 1);
      return XmlTagTextUtil.getCharacterByEntityName(text).charValue();
    }
    text = text.substring(2, text.length() - 1);
    int code;
    if (StringUtil.startsWithChar(text,'x')) {
      text = text.substring(1);
      code = Integer.parseInt(text, 16);
    }
    else {
      code = Integer.parseInt(text);
    }
    return (char)code;
  }

  public int physicalToDisplay(int physicalIndex) {
    getValue();
    if (myGapPhysicalStarts.length == 0) return physicalIndex;

    final int bsResult = Arrays.binarySearch(myGapPhysicalStarts, physicalIndex);

    final int gapIndex;
    if(bsResult > 0) gapIndex = bsResult;
    else if(bsResult < -1) gapIndex = -bsResult - 2;
    else gapIndex = -1;

    if(gapIndex < 0) return physicalIndex;
    final int shift = myGapPhysicalStarts[gapIndex] - myGapDisplayStarts[gapIndex];
    return Math.max(myGapDisplayStarts[gapIndex], physicalIndex - shift);
  }

  public int displayToPhysical(int displayIndex) {
    getValue();
    if (myGapDisplayStarts.length == 0) return displayIndex;

    final int bsResult = Arrays.binarySearch(myGapDisplayStarts, displayIndex);
    final int gapIndex;

    if(bsResult > 0) gapIndex = bsResult - 1;
    else if(bsResult < -1) gapIndex = -bsResult - 2;
    else gapIndex = -1;

    if(gapIndex < 0) return displayIndex;
    final int shift = myGapPhysicalStarts[gapIndex] - myGapDisplayStarts[gapIndex];
    return displayIndex + shift;
  }

  public void setValue(String s) throws IncorrectOperationException {
    final ASTNode firstEncodedElement = getPolicy().encodeXmlTextContents(s, this, SharedImplUtil.findCharTableByTree(this));

    if(firstEncodedElement == null){
      delete();
      return;
    }

    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(this, aspect) {
      public PomModelEvent runInner() throws IncorrectOperationException {
        final String oldText = getText();
        replaceAllChildrenToChildrenOf(firstEncodedElement.getTreeParent());
        clearCaches();
        return XmlTextChangedImpl.createXmlTextChanged(model, XmlTextImpl.this, oldText);
      }
    });
  }

  public XmlElement insertAtOffset(final XmlElement element, final int displayOffset) throws IncorrectOperationException{
    if(element instanceof XmlText){
      insertText(((XmlText)element).getValue(), displayOffset);
    }
    else {
      final PomModel model = getProject().getModel();
      final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
      model.runTransaction(new PomTransactionBase(getParent(), aspect) {
        public PomModelEvent runInner() throws IncorrectOperationException {
          final XmlTagImpl tag = (XmlTagImpl)getParentTag();
          final XmlText rightPart = tag.splitText(XmlTextImpl.this, displayOffset);
          if(rightPart != null) tag.addBefore(element, rightPart);
          else tag.addAfter(element, XmlTextImpl.this);
          return null;
        }
      });
    }

    return this;
  }

  private XmlPsiPolicy getPolicy() {
    return ((XMLLanguage)getLanguage()).getPsiPolicy();
  }

  public void insertText(String text, int displayOffset) throws IncorrectOperationException {
    if(text == null || text.length() == 0) return;
    setValue(new StringBuffer(getValue()).insert(displayOffset, text).toString());
  }

  public void removeText(int displayStart, int displayEnd) throws IncorrectOperationException {
    final String value = getValue();
    if(displayStart == 0 && displayEnd == value.length()) delete();
    else setValue(new StringBuffer(getValue()).replace(displayStart, displayEnd, "").toString());
  }

  public XmlTag getParentTag() {
    final PsiElement parent = getParent();
    if (parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    PsiElement nextSibling = getNextSibling();
    if (nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    PsiElement prevSibling = getPrevSibling();
    if (prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    throw new RuntimeException("Clients must not use operations with direct children of XmlText!");
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlText(this);
  }

  public void clearCaches() {
    super.clearCaches();
    myDisplayText = null;
    myGapDisplayStarts = null;
    myGapPhysicalStarts = null;
  }

  @Nullable
  public Pair<PsiElement,TextRange> getInjectedPsi() {
    String text = getValue();
    TextRange range = new TextRange(0, getTextLength());

    return InjectedLanguageUtil.createInjectedPsiFile(this, text, range);
  }
}
