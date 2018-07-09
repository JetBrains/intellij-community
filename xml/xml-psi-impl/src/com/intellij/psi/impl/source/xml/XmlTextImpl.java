/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.pom.xml.impl.events.XmlTagChildAddImpl;
import com.intellij.pom.xml.impl.events.XmlTextChangedImpl;
import com.intellij.psi.*;
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
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class XmlTextImpl extends XmlElementImpl implements XmlText, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTextImpl");
  private volatile String myDisplayText;
  private volatile int[] myGapDisplayStarts;
  private volatile int[] myGapPhysicalStarts;

  public XmlTextImpl() {
    super(XmlElementType.XML_TEXT);
  }

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
    final TIntArrayList gapsStarts = new TIntArrayList();
    final TIntArrayList gapsShifts = new TIntArrayList();
    while (child != null) {
      final int start = buffer.length();
      IElementType elementType = child.getElementType();
      if (elementType == XmlElementType.XML_CDATA) {
        final ASTNode cdata = child;
        child = cdata.getFirstChildNode();
      }
      else if (elementType == XmlTokenType.XML_CHAR_ENTITY_REF) {
        String text = child.getText();
        LOG.assertTrue(text != null, child);
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
      currentGapsSum += gapsShifts.get(i);
      gapDisplayStarts[i] = gapsStarts.get(i);
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
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(this, aspect) {
      @Override
      public PomModelEvent runInner() {
        final String oldText = getText();
        final ASTNode firstEncodedElement = policy.encodeXmlTextContents(s, XmlTextImpl.this);
        if (firstEncodedElement == null) {
          delete();
        } else {
          replaceAllChildrenToChildrenOf(firstEncodedElement.getTreeParent());
        }
        clearCaches();
        return XmlTextChangedImpl.createXmlTextChanged(model, XmlTextImpl.this, oldText);
      }
    });
  }

  @Override
  public XmlElement insertAtOffset(final XmlElement element, final int displayOffset) throws IncorrectOperationException {
    if (element instanceof XmlText) {
      insertText(((XmlText)element).getValue(), displayOffset);
    }
    else {
      final PomModel model = PomManager.getModel(getProject());
      final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
      model.runTransaction(new PomTransactionBase(getParent(), aspect) {
        @Override
        public PomModelEvent runInner() throws IncorrectOperationException {
          final XmlTag tag = getParentTag();
          assert tag != null;

          final XmlText rightPart = _splitText(displayOffset);
          PsiElement result;
          if (rightPart != null) {
            result = tag.addBefore(element, rightPart);
          }
          else {
            result = tag.addAfter(element, XmlTextImpl.this);
          }
          return createEvent(new XmlTagChildAddImpl(tag, (XmlTagChild)result));
        }
      });
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

      final PomModel model = PomManager.getModel(getProject());
      final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
      model.runTransaction(new PomTransactionBase(this, aspect) {
        @Override
        public PomModelEvent runInner() {
          final String oldText = getText();

          final ASTNode e =
            getPolicy().encodeXmlTextContents(newElementText, XmlTextImpl.this);

          final ASTNode node = psiElement.getNode();
          final ASTNode treeNext = node.getTreeNext();

          addChildren(e, null, treeNext);

          deleteChildInternal(node);


          clearCaches();
          return XmlTextChangedImpl.createXmlTextChanged(model, XmlTextImpl.this, oldText);
        }
      });
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

          final PomModel model = PomManager.getModel(getProject());
          final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
          model.runTransaction(new PomTransactionBase(this, aspect) {
            @Override
            public PomModelEvent runInner() throws IncorrectOperationException {
              final String oldText = getText();

              if (!newElementText.isEmpty()) {
                final ASTNode e =
                  getPolicy().encodeXmlTextContents(newElementText, XmlTextImpl.this);
                replaceChild(psiElement.getNode(), e);
              }
              else {
                psiElement.delete();
              }

              clearCaches();
              return XmlTextChangedImpl.createXmlTextChanged(model, XmlTextImpl.this, oldText);
            }
          });

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

    final PomModel model = PomManager.getModel(xmlTag.getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);

    class MyTransaction extends PomTransactionBase {
      private XmlTextImpl myRight;

      MyTransaction() {
        super(xmlTag, aspect);
      }

      @Override
      @Nullable
      public PomModelEvent runInner() throws IncorrectOperationException {
        final String oldText = getValue();
        final int physicalOffset = displayToPhysical(displayOffset);
        PsiElement childElement = findElementAt(physicalOffset);

        if (childElement != null && childElement.getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
          FileElement holder = DummyHolderFactory.createHolder(getManager(), null).getTreeElement();

          int splitOffset = physicalOffset - childElement.getStartOffsetInParent();
          myRight = (XmlTextImpl)ASTFactory.composite(XmlElementType.XML_TEXT);
          CodeEditUtil.setNodeGenerated(myRight, true);
          holder.rawAddChildren(myRight);

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

          rawInsertAfterMe(myRight);

          myRight.rawAddChildren(rightElement);
          if (childElement.getNextSibling() != null) {
            myRight.rawAddChildren((TreeElement)childElement.getNextSibling());
          }
          ((TreeElement)childElement).rawRemove();
          XmlTextImpl.this.rawAddChildren(leftElement);
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

          myRight = rightText;
        }

        clearCaches();
        myRight.clearCaches();
        return createEvent(new XmlTextChangedImpl(XmlTextImpl.this, oldText), new XmlTagChildAddImpl(xmlTag, myRight));
      }

      public XmlText getResult() {
        return myRight;
      }
    }
    final MyTransaction transaction = new MyTransaction();
    model.runTransaction(transaction);

    return transaction.getResult();
  }

  private PomModelEvent createEvent(final XmlChange...events) {
    final PomModelEvent event = new PomModelEvent(PomManager.getModel(getProject()));

    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(PomManager.getModel(getProject()), (XmlFile)getContainingFile());

    for (XmlChange xmlChange : events) {
      xmlAspectChangeSet.add(xmlChange);
    }

    event.registerChangeSet(PomManager.getModel(getProject()).getModelAspect(XmlAspect.class), xmlAspectChangeSet);

    return event;
  }

  @Override
  @NotNull
  public LiteralTextEscaper<XmlTextImpl> createLiteralTextEscaper() {
    return new XmlTextLiteralEscaper(this);
  }
}
