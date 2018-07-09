/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.ASTNode;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.pom.xml.impl.events.XmlAttributeSetImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

/**
 * @author Mike
 */
public class XmlAttributeImpl extends XmlElementImpl implements XmlAttribute, HintedReferenceHost {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttributeImpl");

  private final int myHC = ourHC++;

  @Override
  public final int hashCode() {
    return myHC;
  }

  public XmlAttributeImpl() {
    super(XmlElementType.XML_ATTRIBUTE);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XmlTokenType.XML_NAME) {
      return XmlChildRole.XML_NAME;
    }
    else if (i == XmlElementType.XML_ATTRIBUTE_VALUE) {
      return XmlChildRole.XML_ATTRIBUTE_VALUE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public XmlAttributeValue getValueElement() {
    return (XmlAttributeValue)XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(this);
  }

  @Override
  public void setValue(String valueText) throws IncorrectOperationException {
    final ASTNode value = XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(this);
    final PomModel model = PomManager.getModel(getProject());
    final XmlAttribute attribute = XmlElementFactory.getInstance(getProject()).createAttribute("a", valueText, this);
    final ASTNode newValue = XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild((ASTNode)attribute);
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(this, aspect) {
      @Override
      public PomModelEvent runInner() {
        final XmlAttributeImpl att = XmlAttributeImpl.this;
        if (value != null) {
          if (newValue != null) {
            att.replaceChild(value, newValue.copyElement());
          }
          else {
            att.removeChild(value);
          }
        }
        else {
          if (newValue != null) {
            att.addChild(newValue.getTreePrev().copyElement());
            att.addChild(newValue.copyElement());
          }
        }
        return XmlAttributeSetImpl.createXmlAttributeSet(model, getParent(), getName(), newValue != null ? newValue.getText() : null);
      }
    });
  }

  @Override
  public XmlElement getNameElement() {
    return (XmlElement)XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(this);
  }

  @Override
  @NotNull
  public String getNamespace() {
    final String name = getName();
    final String prefixByQualifiedName = XmlUtil.findPrefixByQualifiedName(name);
    // The namespace name for an unprefixed attribute name always has no value. Namespace recommendation section 6.2, third paragraph
    if (prefixByQualifiedName.isEmpty()) return XmlUtil.EMPTY_URI;
    return getParent().getNamespaceByPrefix(prefixByQualifiedName);
  }

  @Override
  @NonNls
  @NotNull
  public String getNamespacePrefix() {
    return XmlUtil.findPrefixByQualifiedName(getName());
  }

  @Override
  public XmlTag getParent() {
    final PsiElement parentTag = super.getParent();
    return parentTag instanceof XmlTag ? (XmlTag)parentTag : null; // Invalid elements might belong to DummyHolder instead.
  }

  @Override
  @NotNull
  public String getLocalName() {
    return XmlUtil.findLocalNameByQualifiedName(getName());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlAttribute(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String getValue() {
    final XmlAttributeValue valueElement = getValueElement();
    return valueElement != null ? valueElement.getValue() : null;
  }

  private volatile VolatileState myVolatileState;

  protected void appendChildToDisplayValue(StringBuilder buffer, ASTNode child) {
    buffer.append(child.getChars());
  }

  @Override
  @Nullable
  public String getDisplayValue() {
    final VolatileState state = getFreshState();
    return state == null ? null : state.myDisplayText;
  }

  @Override
  public int physicalToDisplay(int physicalIndex) {
    final VolatileState state = getFreshState();
    if (state == null) return -1;
    if (physicalIndex < 0 || physicalIndex > state.myValueTextRange.getLength()) return -1;
    if (state.myGapPhysicalStarts.length == 0) return physicalIndex;

    final int bsResult = Arrays.binarySearch(state.myGapPhysicalStarts, physicalIndex);

    final int gapIndex;
    if (bsResult > 0) {
      gapIndex = bsResult;
    }
    else if (bsResult < -1) {
      gapIndex = -bsResult - 2;
    }
    else {
      gapIndex = -1;
    }

    if (gapIndex < 0) return physicalIndex;
    final int shift = state.myGapPhysicalStarts[gapIndex] - state.myGapDisplayStarts[gapIndex];
    return Math.max(state.myGapDisplayStarts[gapIndex], physicalIndex - shift);
  }

  @Override
  public int displayToPhysical(int displayIndex) {
    final VolatileState state = getFreshState();
    if (state == null) return -1;
    final String displayValue = state.myDisplayText;
    if (displayIndex < 0 || displayIndex > displayValue.length()) return -1;

    final int[] gapDisplayStarts = state.myGapDisplayStarts;
    if (gapDisplayStarts.length == 0) return displayIndex;

    final int bsResult = Arrays.binarySearch(gapDisplayStarts, displayIndex);
    final int gapIndex;

    if (bsResult > 0) {
      gapIndex = bsResult - 1;
    }
    else if (bsResult < -1) {
      gapIndex = -bsResult - 2;
    }
    else {
      gapIndex = -1;
    }

    if (gapIndex < 0) return displayIndex;
    final int shift = state.myGapPhysicalStarts[gapIndex] - gapDisplayStarts[gapIndex];
    return displayIndex + shift;
  }

  @NotNull
  @Override
  public TextRange getValueTextRange() {
    final VolatileState state = getFreshState();
    return state == null ? new TextRange(0,0) : state.myValueTextRange;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myVolatileState = null;
  }

  @Override
  @NotNull
  public String getName() {
    XmlElement element = getNameElement();
    return element != null ? element.getText() : "";
  }

  @Override
  public boolean isNamespaceDeclaration() {
    @NonNls final String name = getName();
    return name.startsWith("xmlns:") || name.equals("xmlns");
  }

  @Override
  public PsiElement setName(@NotNull final String nameText) throws IncorrectOperationException {
    final ASTNode name = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(this);
    final String oldName = name == null ? "" : name.getText();
    final String oldValue = ObjectUtils.notNull(getValue(), "");
    final PomModel model = PomManager.getModel(getProject());
    final XmlAttribute attribute = XmlElementFactory.getInstance(getProject()).createAttribute(nameText, oldValue, this);
    final ASTNode newName = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild((ASTNode)attribute);
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    final Ref<XmlAttribute> replaced = Ref.create(this);
    model.runTransaction(new PomTransactionBase(getParent(), aspect) {
      @Override
      public PomModelEvent runInner() {
        final PomModelEvent event = new PomModelEvent(model);
        PsiFile file = getContainingFile();
        XmlChangeSet xmlAspectChangeSet = new XmlAspectChangeSetImpl(model, file instanceof XmlFile ? (XmlFile)file : null);
        xmlAspectChangeSet.add(new XmlAttributeSetImpl(getParent(), oldName, null));
        xmlAspectChangeSet.add(new XmlAttributeSetImpl(getParent(), nameText, oldValue));
        event.registerChangeSet(model.getModelAspect(XmlAspect.class), xmlAspectChangeSet);
        if (!oldValue.isEmpty() && getLanguage().isKindOf(HTMLLanguage.INSTANCE)) {
          CodeEditUtil.replaceChild(getTreeParent(), XmlAttributeImpl.this, attribute.getNode());
          replaced.set(attribute);
        }
        else if (name != null && newName != null) {
          CodeEditUtil.replaceChild(XmlAttributeImpl.this, name, newName);
        }
        return event;
      }
    });
    return replaced.get();
  }

  @Override
  public PsiReference getReference() {
    final PsiReference[] refs = getReferences(PsiReferenceService.Hints.NO_HINTS);
    if (refs.length > 0) return refs[0];
    return null;
  }

  @Override
  public boolean shouldAskParentForReferences(@NotNull PsiReferenceService.Hints hints) {
    return false;
  }

  /**
   * Use {@link #getReferences(PsiReferenceService.Hints)} instead of calling or overriding this method.
   */
  @Deprecated
  @NotNull
  @Override
  public final PsiReference[] getReferences() {
    return getReferences(PsiReferenceService.Hints.NO_HINTS);
  }

  @NotNull
  @Override
  public PsiReference[] getReferences(@NotNull PsiReferenceService.Hints hints) {
    if (hints.offsetInElement != null) {
      XmlElement nameElement = getNameElement();
      if (nameElement == null || hints.offsetInElement > nameElement.getStartOffsetInParent() + nameElement.getTextLength()) {
        return PsiReference.EMPTY_ARRAY;
      }
    }

    final PsiReference[] referencesFromProviders = ReferenceProvidersRegistry.getReferencesFromProviders(this);
    PsiReference[] refs;
    if (isNamespaceDeclaration()) {
      refs = new PsiReference[referencesFromProviders.length + 1];
      final String localName = getLocalName();
      final String prefix = XmlUtil.findPrefixByQualifiedName(getName());
      final TextRange range =
        prefix.isEmpty() ? TextRange.from(getName().length(), 0) : TextRange.from(prefix.length() + 1, localName.length());
      refs[0] = new SchemaPrefixReference(this, range, localName, null);
    }
    else {
      final String prefix = getNamespacePrefix();
      if (!prefix.isEmpty() && !getLocalName().isEmpty()) {
        refs = new PsiReference[referencesFromProviders.length + 2];
        refs[0] = new SchemaPrefixReference(this, TextRange.from(0, prefix.length()), prefix, null);
        refs[1] = new XmlAttributeReference(this);
      }
      else {
        refs = new PsiReference[referencesFromProviders.length + 1];
        refs[0] = new XmlAttributeReference(this);
      }
    }
    System.arraycopy(referencesFromProviders, 0, refs, refs.length - referencesFromProviders.length, referencesFromProviders.length);
    return refs;
  }

  @Override
  @Nullable
  public XmlAttributeDescriptor getDescriptor() {
    return CachedValuesManager.getCachedValue(this,
                                              () -> CachedValueProvider.Result.create(getDescriptorImpl(),
                                                                                      PsiModificationTracker.MODIFICATION_COUNT,
                                                                                      externalResourceModificationTracker()));
  }

  private ModificationTracker externalResourceModificationTracker() {
    Project project = getProject();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    return () -> manager.getModificationCount(project);
  }


  @Nullable
  private XmlAttributeDescriptor getDescriptorImpl() {
    final XmlTag tag = getParent();
    if (tag == null) return null; // e.g. XmlDecl or PI
    final XmlElementDescriptor descr = tag.getDescriptor();
    if (descr == null) return null;
    final XmlAttributeDescriptor attributeDescr = descr.getAttributeDescriptor(this);
    return attributeDescr == null ? descr.getAttributeDescriptor(getName(), tag) : attributeDescr;
  }

  public String getRealLocalName() {
    final String name = getLocalName();
    return name.endsWith(DUMMY_IDENTIFIER_TRIMMED) ? name.substring(0, name.length() - DUMMY_IDENTIFIER_TRIMMED.length()) : name;
  }

  @Nullable
  private VolatileState getFreshState() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    VolatileState state = myVolatileState;
    if (state == null) {
      state = recalculate();
    }
    return state;
  }

  @Nullable
  private VolatileState recalculate() {
    XmlAttributeValue value = getValueElement();
    if (value == null) return null;
    PsiElement firstChild = value.getFirstChild();
    if (firstChild == null) return null;
    ASTNode child = firstChild.getNode();
    TextRange valueTextRange = new TextRange(0, value.getTextLength());
    if (child != null && child.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      valueTextRange = new TextRange(child.getTextLength(), valueTextRange.getEndOffset());
      child = child.getTreeNext();
    }
    final TIntArrayList gapsStarts = new TIntArrayList();
    final TIntArrayList gapsShifts = new TIntArrayList();
    StringBuilder buffer = new StringBuilder(getTextLength());
    while (child != null) {
      final int start = buffer.length();
      IElementType elementType = child.getElementType();
      if (elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        valueTextRange =
          new TextRange(valueTextRange.getStartOffset(), child.getTextRange().getStartOffset() - value.getTextRange().getStartOffset());
        break;
      }
      if (elementType == XmlTokenType.XML_CHAR_ENTITY_REF) {
        buffer.append(XmlUtil.getCharFromEntityRef(child.getText()));
      }
      else if (elementType == XmlElementType.XML_ENTITY_REF) {
        buffer.append(XmlUtil.getEntityValue((XmlEntityRef)child));
      }
      else {
        appendChildToDisplayValue(buffer, child);
      }

      int end = buffer.length();
      int originalLength = child.getTextLength();
      if (end - start != originalLength) {
        gapsStarts.add(start);
        gapsShifts.add(originalLength - (end - start));
      }
      child = child.getTreeNext();
    }
    int[] gapDisplayStarts = ArrayUtil.newIntArray(gapsShifts.size());
    int[] gapPhysicalStarts = ArrayUtil.newIntArray(gapsShifts.size());
    int currentGapsSum = 0;
    for (int i = 0; i < gapDisplayStarts.length; i++) {
      currentGapsSum += gapsShifts.get(i);
      gapDisplayStarts[i] = gapsStarts.get(i);
      gapPhysicalStarts[i] = gapDisplayStarts[i] + currentGapsSum;
    }
    final VolatileState volatileState = new VolatileState(buffer.toString(), gapDisplayStarts, gapPhysicalStarts, valueTextRange);
    myVolatileState = volatileState;
    return volatileState;
  }

  private static class VolatileState {
    @NotNull private final String myDisplayText;
    @NotNull private final int[] myGapDisplayStarts;
    @NotNull private final int[] myGapPhysicalStarts;
    @NotNull private final TextRange myValueTextRange; // text inside quotes, if there are any

    private VolatileState(@NotNull final String displayText,
                          @NotNull int[] gapDisplayStarts,
                          @NotNull int[] gapPhysicalStarts,
                          @NotNull TextRange valueTextRange) {
      myDisplayText = displayText;
      myGapDisplayStarts = gapDisplayStarts;
      myGapPhysicalStarts = gapPhysicalStarts;
      myValueTextRange = valueTextRange;
    }
  }
}
