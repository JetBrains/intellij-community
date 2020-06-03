// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.injected.XmlTextLiteralEscaper;
import com.intellij.psi.impl.source.xml.behavior.DefaultXmlPsiPolicy;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class XmlTextImpl extends XmlElementImpl implements XmlText, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance(XmlTextImpl.class);
  private volatile String myDisplayText;
  private volatile int[] myGapDisplayStarts;
  private volatile int[] myGapPhysicalStarts;

  public XmlTextImpl() {
    super(XmlElementType.XML_TEXT);
  }

  @Override
  public String toString() {
    return "XmlText";
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  @Nullable
  public XmlText split(int displayIndex) {
    try {
      return _splitText(displayIndex);
    }
    catch (IncorrectOperationException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public String getValue() {
    String displayText = myDisplayText;
    if (displayText != null) return displayText;
    StringBuilder buffer = new StringBuilder();
    ASTNode child = getFirstChildNode();
    final IntList gapsStarts = new IntArrayList();
    final IntList gapsShifts = new IntArrayList();
    while (child != null) {
      final int start = buffer.length();
      IElementType elementType = child.getElementType();
      if (elementType == XmlElementType.XML_CDATA) {
        final ASTNode cdata = child;
        child = cdata.getFirstChildNode();
      }
      else if (elementType == XmlTokenType.XML_CHAR_ENTITY_REF) {
        String text = child.getText();
        buffer.append(XmlUtil.getCharFromEntityRef(text));
      }
      else if (elementType == XmlTokenType.XML_WHITE_SPACE ||
               elementType == XmlTokenType.XML_DATA_CHARACTERS ||
               elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
               elementType == TokenType.ERROR_ELEMENT ||
               elementType == TokenType.NEW_LINE_INDENT) {
        buffer.append(child.getText());
      }

      int end = buffer.length();
      int originalLength = child.getTextLength();
      if (end - start != originalLength) {
        gapsStarts.add(end);
        gapsShifts.add(originalLength - (end - start));
      }
      final ASTNode next = child.getTreeNext();
      if (next == null && child.getTreeParent().getElementType() == XmlElementType.XML_CDATA) {
        child = child.getTreeParent().getTreeNext();
      }
      else {
        child = next;
      }
    }
    int[] gapDisplayStarts = ArrayUtil.newIntArray(gapsShifts.size());
    int[] gapPhysicalStarts = ArrayUtil.newIntArray(gapsShifts.size());
    int currentGapsSum = 0;
    for (int i = 0; i < gapDisplayStarts.length; i++) {
      currentGapsSum += gapsShifts.getInt(i);
      gapDisplayStarts[i] = gapsStarts.getInt(i);
      gapPhysicalStarts[i] = gapDisplayStarts[i] + currentGapsSum;
    }
    myGapDisplayStarts = gapDisplayStarts;
    myGapPhysicalStarts = gapPhysicalStarts;
    String text = buffer.toString();
    myDisplayText = text;
    return text;
  }

  @Override
  public int physicalToDisplay(int physicalIndex) {
    getValue();
    if (myGapPhysicalStarts.length == 0) return physicalIndex;

    final int bsResult = Arrays.binarySearch(myGapPhysicalStarts, physicalIndex);

    if (bsResult >= 0) return myGapDisplayStarts[bsResult];

    int insertionIndex = -bsResult - 1;

    //if (insertionIndex == myGapDisplayStarts.length) return getValue().length();

    int prevPhysGapStart = insertionIndex > 0 ? myGapPhysicalStarts[insertionIndex - 1] : 0;
    int prevDisplayGapStart = insertionIndex > 0 ? myGapDisplayStarts[insertionIndex - 1] : 0;

    if (insertionIndex < myGapDisplayStarts.length) {
      int prevDisplayGapLength =
        insertionIndex > 0 ? myGapDisplayStarts[insertionIndex] - myGapDisplayStarts[insertionIndex - 1] : myGapDisplayStarts[0];
      if (physicalIndex - prevPhysGapStart > prevDisplayGapLength) return myGapDisplayStarts[insertionIndex];
    }

    return physicalIndex - prevPhysGapStart + prevDisplayGapStart;
  }

  @Override
  public int displayToPhysical(int displayIndex) {
    getValue();
    if (myGapDisplayStarts.length == 0) return displayIndex;

    final int bsResult = Arrays.binarySearch(myGapDisplayStarts, displayIndex);
    if (bsResult >= 0) return myGapPhysicalStarts[bsResult];

    int insertionIndex = -bsResult - 1;
    int prevPhysGapStart = insertionIndex > 0 ? myGapPhysicalStarts[insertionIndex - 1] : 0;
    int prevDisplayGapStart = insertionIndex > 0 ? myGapDisplayStarts[insertionIndex - 1] : 0;
    return displayIndex - prevDisplayGapStart + prevPhysGapStart;
  }

  @Override
  public void setValue(String s) throws IncorrectOperationException {
    doSetValue(s, getPolicy());
  }

  public void doSetValue(final String s, final XmlPsiPolicy policy) throws IncorrectOperationException {
    final ASTNode firstEncodedElement = policy.encodeXmlTextContents(s, this);
    if (firstEncodedElement == null) {
      delete();
    } else {
      replaceAllChildrenToChildrenOf(firstEncodedElement.getTreeParent());
    }
    clearCaches();
  }

  @Override
  public XmlElement insertAtOffset(final XmlElement element, final int displayOffset) throws IncorrectOperationException {
    if (element instanceof XmlText) {
      insertText(((XmlText)element).getValue(), displayOffset);
    }
    else {
      final XmlTag tag = getParentTag();
      assert tag != null;

      final XmlText rightPart = _splitText(displayOffset);
      if (rightPart != null) {
        tag.addBefore(element, rightPart);
      }
      else {
        tag.addAfter(element, this);
      }
    }

    return this;
  }

  private XmlPsiPolicy getPolicy() {
    return LanguageXmlPsiPolicy.INSTANCE.forLanguage(getLanguage());
  }

  @Override
  public void insertText(String text, int displayOffset) throws IncorrectOperationException {
    if (text == null || text.isEmpty()) return;

    final int physicalOffset = displayToPhysical(displayOffset);
    final PsiElement psiElement = findElementAt(physicalOffset);
    //if (!(psiElement instanceof XmlTokenImpl)) throw new IncorrectOperationException("Can't insert at offset: " + displayOffset);
    final IElementType elementType = psiElement != null ? psiElement.getNode().getElementType() : null;

    if (elementType == XmlTokenType.XML_DATA_CHARACTERS) {
      int insertOffset = physicalOffset - psiElement.getStartOffsetInParent();

      final String oldElementText = psiElement.getText();
      final String newElementText = oldElementText.substring(0, insertOffset) + text + oldElementText.substring(insertOffset);

      final ASTNode e =
        getPolicy().encodeXmlTextContents(newElementText, this);

      final ASTNode node = psiElement.getNode();
      final ASTNode treeNext = node.getTreeNext();

      addChildren(e, null, treeNext);

      deleteChildInternal(node);


      clearCaches();
    }
    else {
      setValue(new StringBuffer(getValue()).insert(displayOffset, text).toString());
    }
  }

  @Override
  public void removeText(int displayStart, int displayEnd) throws IncorrectOperationException {
    final String value = getValue();

    final int physicalStart = displayToPhysical(displayStart);
    final PsiElement psiElement = findElementAt(physicalStart);
    if (psiElement != null) {
      final IElementType elementType = psiElement.getNode().getElementType();
      final int elementDisplayEnd = physicalToDisplay(psiElement.getStartOffsetInParent() + psiElement.getTextLength());
      final int elementDisplayStart = physicalToDisplay(psiElement.getStartOffsetInParent());
      if (elementType == XmlTokenType.XML_DATA_CHARACTERS || elementType == TokenType.WHITE_SPACE) {
        if (elementDisplayEnd >= displayEnd && elementDisplayStart <= displayStart) {
          int physicalEnd = physicalStart;
          while (physicalEnd < getTextRange().getLength()) {
            if (physicalToDisplay(physicalEnd) == displayEnd) break;
            physicalEnd++;
          }

          int removeStart = physicalStart - psiElement.getStartOffsetInParent();
          int removeEnd = physicalEnd - psiElement.getStartOffsetInParent();

          final String oldElementText = psiElement.getText();
          final String newElementText = oldElementText.substring(0, removeStart) + oldElementText.substring(removeEnd);

          if (!newElementText.isEmpty()) {
            final ASTNode e =
              getPolicy().encodeXmlTextContents(newElementText, this);
            replaceChild(psiElement.getNode(), e);
          }
          else {
            psiElement.delete();
          }

          clearCaches();
          return;
        }
      }
    }

    if (displayStart == 0 && displayEnd == value.length()) {
      delete();
    }
    else {
      setValue(new StringBuffer(getValue()).replace(displayStart, displayEnd, "").toString());
    }
  }

  @Override
  public XmlTag getParentTag() {
    final PsiElement parent = getParent();
    if (parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    PsiElement nextSibling = getNextSibling();
    if (nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    PsiElement prevSibling = getPrevSibling();
    if (prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    throw new RuntimeException("Clients must not use operations with direct children of XmlText!");
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlText(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myDisplayText = null;
    myGapDisplayStarts = null;
    myGapPhysicalStarts = null;
  }

  public TextRange getCDATAInterior() {
    PsiElement[] elements = getChildren();
    int first = 0;
    if (elements.length > 0 && elements[0] instanceof PsiWhiteSpace) {
      first ++;
    }
    int start = 0;
    if (elements.length > first && elements[first].getNode().getElementType() == XmlElementType.XML_CDATA) {
      ASTNode startNode = elements[first].getNode().findChildByType(XmlTokenType.XML_CDATA_START);
      if (startNode != null) {
        start = startNode.getTextRange().getEndOffset() - getTextRange().getStartOffset();
      }
    }
    int end = getTextLength();
    int last = elements.length - 1;
    if (last > 0 && elements[last] instanceof PsiWhiteSpace) {
      last --;
    }
    if (last >= 0 && elements[last].getNode().getElementType() == XmlElementType.XML_CDATA) {
      ASTNode startNode = elements[last].getNode().findChildByType(XmlTokenType.XML_CDATA_END);
      if (startNode != null) {
        end = startNode.getTextRange().getStartOffset() - getTextRange().getStartOffset();
      }
    }

    return new TextRange(start, end);
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull final String text) {
    try {
      doSetValue(text, new DefaultXmlPsiPolicy());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return this;
  }

  @Nullable
  private XmlText _splitText(final int displayOffset) throws IncorrectOperationException{
    final XmlTag xmlTag = (XmlTag)getParent();
    if(displayOffset == 0) return this;
    final int length = getValue().length();
    if(displayOffset >= length) {
      return null;
    }

    XmlTextImpl result;

    final int physicalOffset = displayToPhysical(displayOffset);
    PsiElement childElement = findElementAt(physicalOffset);

    if (childElement != null && childElement.getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
      FileElement holder = DummyHolderFactory.createHolder(getManager(), null).getTreeElement();

      int splitOffset = physicalOffset - childElement.getStartOffsetInParent();
      result = (XmlTextImpl)ASTFactory.composite(XmlElementType.XML_TEXT);
      CodeEditUtil.setNodeGenerated(result, true);
      holder.rawAddChildren(result);

      PsiElement e = childElement;
      while (e != null) {
        CodeEditUtil.setNodeGenerated(e.getNode(), true);
        e = e.getNextSibling();
      }

      String leftText = childElement.getText().substring(0, splitOffset);
      String rightText = childElement.getText().substring(splitOffset);


      LeafElement rightElement =
        ASTFactory.leaf(XmlTokenType.XML_DATA_CHARACTERS, holder.getCharTable().intern(rightText));
      CodeEditUtil.setNodeGenerated(rightElement, true);

      LeafElement leftElement = ASTFactory.leaf(XmlTokenType.XML_DATA_CHARACTERS, holder.getCharTable().intern(leftText));
      CodeEditUtil.setNodeGenerated(leftElement, true);

      rawInsertAfterMe(result);

      result.rawAddChildren(rightElement);
      if (childElement.getNextSibling() != null) {
        result.rawAddChildren((TreeElement)childElement.getNextSibling());
      }
      DebugUtil.performPsiModification("xmlText split",  () -> ((TreeElement)childElement).rawRemove());
      this.rawAddChildren(leftElement);
    }
    else {
      final PsiFile containingFile = xmlTag.getContainingFile();
      final FileElement holder = DummyHolderFactory
        .createHolder(containingFile.getManager(), null, ((PsiFileImpl)containingFile).getTreeElement().getCharTable()).getTreeElement();
      final XmlTextImpl rightText = (XmlTextImpl)ASTFactory.composite(XmlElementType.XML_TEXT);
      CodeEditUtil.setNodeGenerated(rightText, true);

      holder.rawAddChildren(rightText);

      ((ASTNode)xmlTag).addChild(rightText, getTreeNext());

      final String value = getValue();

      setValue(value.substring(0, displayOffset));
      rightText.setValue(value.substring(displayOffset));

      CodeEditUtil.setNodeGenerated(rightText, true);

      result = rightText;
    }

    clearCaches();
    result.clearCaches();
    return result;
  }

  @Override
  @NotNull
  public LiteralTextEscaper<XmlTextImpl> createLiteralTextEscaper() {
    return getParentTag() instanceof HtmlTag ?
           LiteralTextEscaper.createSimple(this) :
           new XmlTextLiteralEscaper(this);
  }
}
