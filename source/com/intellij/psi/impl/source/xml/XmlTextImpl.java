package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.pom.xml.impl.events.XmlTagChildAddImpl;
import com.intellij.pom.xml.impl.events.XmlTextChangedImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.xml.behavior.DefaultXmlPsiPolicy;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class XmlTextImpl extends XmlElementImpl implements XmlText, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTextImpl");
  private String myDisplayText = null;
  private int[] myGapDisplayStarts = null;
  private int[] myGapPhysicalStarts = null;

  public XmlTextImpl() {
    super(XmlElementType.XML_TEXT);
  }

  public String toString() {
    return "XmlText";
  }

  @Nullable
  public XmlText split(int displayIndex) {
    try {
      return _splitText(displayIndex);
    }
    catch (IncorrectOperationException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public String getValue() {
    if (myDisplayText != null) return myDisplayText;
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
        buffer.append(XmlUtil.getCharFromEntityRef(child.getText()));
      }
      else if (elementType == XmlTokenType.XML_WHITE_SPACE || elementType == XmlTokenType.XML_DATA_CHARACTERS || elementType == XmlTokenType
        .XML_ATTRIBUTE_VALUE_TOKEN) {
        buffer.append(child.getText());
      }
      else if (elementType == JavaElementType.ERROR_ELEMENT) {
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
    myGapDisplayStarts = new int[gapsShifts.size()];
    myGapPhysicalStarts = new int[gapsShifts.size()];
    int currentGapsSum = 0;
    for (int i = 0; i < myGapDisplayStarts.length; i++) {
      currentGapsSum += gapsShifts.get(i);
      myGapDisplayStarts[i] = gapsStarts.get(i);
      myGapPhysicalStarts[i] = myGapDisplayStarts[i] + currentGapsSum;
    }

    return myDisplayText = buffer.toString();
  }

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

  public int displayToPhysical(int displayIndex) {
    getValue();
    if (myGapDisplayStarts.length == 0) return displayIndex;

    final int bsResult = Arrays.binarySearch(myGapDisplayStarts, displayIndex);
    if (bsResult >= 0) return myGapPhysicalStarts[bsResult];

    int insertionIndex = -bsResult - 1;
    int prevPhysGapStart = insertionIndex > 0 ? myGapPhysicalStarts[insertionIndex - 1] : 0;
    int prevDisplayGapStart = insertionIndex > 0 ? myGapDisplayStarts[insertionIndex - 1] : 0;
    return (displayIndex - prevDisplayGapStart) + prevPhysGapStart;
  }

  public void setValue(String s) throws IncorrectOperationException {
    doSetValue(s, getPolicy());
  }

  private void doSetValue(final String s, XmlPsiPolicy policy) throws IncorrectOperationException {
    final ASTNode firstEncodedElement = policy.encodeXmlTextContents(s, this, SharedImplUtil.findCharTableByTree(this));

    if (firstEncodedElement == null) {
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

  public XmlElement insertAtOffset(final XmlElement element, final int displayOffset) throws IncorrectOperationException {
    if (element instanceof XmlText) {
      insertText(((XmlText)element).getValue(), displayOffset);
    }
    else {
      final PomModel model = getProject().getModel();
      final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
      model.runTransaction(new PomTransactionBase(getParent(), aspect) {
        public PomModelEvent runInner() throws IncorrectOperationException {
          final XmlTagImpl tag = (XmlTagImpl)getParentTag();
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
    return ((XMLLanguage)getLanguage()).getPsiPolicy();
  }

  public void insertText(String text, int displayOffset) throws IncorrectOperationException {
    if (text == null || text.length() == 0) return;

    final int physicalOffset = displayToPhysical(displayOffset);
    final PsiElement psiElement = findElementAt(physicalOffset);
    //if (!(psiElement instanceof XmlTokenImpl)) throw new IncorrectOperationException("Can't insert at offset: " + displayOffset);
    final IElementType elementType = psiElement != null ? psiElement.getNode().getElementType() : null;

    if (elementType == XmlTokenType.XML_DATA_CHARACTERS) {
      int insertOffset = physicalOffset - psiElement.getStartOffsetInParent();

      final String oldElementText = psiElement.getText();
      final String newElementText = oldElementText.substring(0, insertOffset) + text + oldElementText.substring(insertOffset);

      final PomModel model = getProject().getModel();
      final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
      model.runTransaction(new PomTransactionBase(this, aspect) {
        public PomModelEvent runInner() throws IncorrectOperationException {
          final String oldText = getText();

          final ASTNode e =
            getPolicy().encodeXmlTextContents(newElementText, XmlTextImpl.this, SharedImplUtil.findCharTableByTree(XmlTextImpl.this));

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

  public void removeText(int displayStart, int displayEnd) throws IncorrectOperationException {
    final String value = getValue();

    final int physicalStart = displayToPhysical(displayStart);
    final PsiElement psiElement = findElementAt(physicalStart);
    if (psiElement != null) {
      final IElementType elementType = psiElement.getNode().getElementType();
      final int elementDisplayEnd = physicalToDisplay(psiElement.getStartOffsetInParent() + psiElement.getTextLength());
      final int elementDisplayStart = physicalToDisplay(psiElement.getStartOffsetInParent());
      if (elementType == XmlTokenType.XML_DATA_CHARACTERS || elementType == JavaTokenType.WHITE_SPACE) {
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

          final PomModel model = getProject().getModel();
          final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
          model.runTransaction(new PomTransactionBase(this, aspect) {
            public PomModelEvent runInner() throws IncorrectOperationException {
              final String oldText = getText();

              if (newElementText.length() > 0) {
                final ASTNode e =
                  getPolicy().encodeXmlTextContents(newElementText, XmlTextImpl.this, SharedImplUtil.findCharTableByTree(XmlTextImpl.this));
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
  public List<Pair<PsiElement, TextRange>> getInjectedPsi() {
    return InjectedLanguageUtil.getInjectedPsiFiles(this, InjectedLanguageUtil.XmlTextLiteralEscaper.INSTANCE);
  }

  public void fixText(final String text) {
    try {
      doSetValue(text, new DefaultXmlPsiPolicy());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  protected XmlText _splitText(final int displayOffset) throws IncorrectOperationException{
    final XmlTag xmlTag = (XmlTag)getParent();
    if(displayOffset == 0) return this;
    final int length = this.getValue().length();
    if(displayOffset >= length) {
      return null;
    }

    final PomModel model = xmlTag.getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);

    class MyTransaction extends PomTransactionBase {
      private XmlTextImpl myRight;

      public MyTransaction() {
        super(xmlTag, aspect);
      }

      @Nullable
      public PomModelEvent runInner() throws IncorrectOperationException {
        final String oldText = getValue();
        final int physicalOffset = displayToPhysical(displayOffset);
        PsiElement childElement = findElementAt(physicalOffset);

        if (childElement != null && childElement.getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
          FileElement holder = new DummyHolder(getManager(), null).getTreeElement();

          int splitOffset = physicalOffset - childElement.getStartOffsetInParent();
          myRight = (XmlTextImpl)Factory.createCompositeElement(XmlElementType.XML_TEXT);
          CodeEditUtil.setNodeGenerated(myRight, true);
          TreeUtil.addChildren(holder, myRight);

          PsiElement e = childElement;
          while (e != null) {
            CodeEditUtil.setNodeGenerated(e.getNode(), true);
            e = e.getNextSibling();
          }

          String leftText = childElement.getText().substring(0, splitOffset);
          String rightText = childElement.getText().substring(splitOffset);


          LeafElement rightElement = Factory.createLeafElement(XmlTokenType.XML_DATA_CHARACTERS, rightText.toCharArray(), 0, rightText.length(), -1,
                                                               holder.getCharTable());
          CodeEditUtil.setNodeGenerated(rightElement, true);

          LeafElement leftElement = Factory.createLeafElement(XmlTokenType.XML_DATA_CHARACTERS, leftText.toCharArray(), 0, leftText.length(), -1,
                                                              holder.getCharTable());
          CodeEditUtil.setNodeGenerated(leftElement, true);

          TreeUtil.insertBefore(XmlTextImpl.this.getTreeNext(), myRight);

          TreeUtil.addChildren(myRight, rightElement);
          if (childElement.getNextSibling() != null) {
            TreeUtil.addChildren(myRight, (TreeElement)childElement.getNextSibling());
          }
          TreeUtil.remove(((TreeElement)childElement));
          TreeUtil.addChildren(XmlTextImpl.this, leftElement);
        }
        else {
          final PsiFile containingFile = xmlTag.getContainingFile();
          final FileElement holder = new DummyHolder(containingFile.getManager(), null, ((PsiFileImpl)containingFile).getTreeElement().getCharTable()).getTreeElement();
          final XmlTextImpl rightText = (XmlTextImpl)Factory.createCompositeElement(XmlElementType.XML_TEXT);
          CodeEditUtil.setNodeGenerated(rightText, true);

          TreeUtil.addChildren(holder, rightText);

          ((XmlTagImpl)xmlTag).addChild(rightText, XmlTextImpl.this.getTreeNext());

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

  protected PomModelEvent createEvent(final XmlChange...events) {
    final PomModelEvent event = new PomModelEvent(getProject().getModel());

    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(getProject().getModel(), (XmlFile)getContainingFile());

    for (XmlChange xmlChange : events) {
      xmlAspectChangeSet.add(xmlChange);
    }

    event.registerChangeSet(getProject().getModel().getModelAspect(XmlAspect.class), xmlAspectChangeSet);

    return event;
  }
}
