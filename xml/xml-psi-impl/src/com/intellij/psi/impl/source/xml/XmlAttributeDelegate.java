// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.ASTNode;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformUtils;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.util.ObjectUtils.doIfNotNull;
import static com.intellij.util.ObjectUtils.notNull;

@ApiStatus.Experimental
public abstract class XmlAttributeDelegate {
  private final @NotNull XmlAttribute myAttribute;

  private volatile VolatileState myVolatileState;

  XmlAttributeDelegate(@NotNull XmlAttribute attribute) {
    myAttribute = attribute;
  }

  @Nullable
  XmlAttributeDescriptor getDescriptor() {
    XmlAttribute attribute = myAttribute;
    return CachedValuesManager.getCachedValue(
      attribute,
      () -> CachedValueProvider.Result.create(getDescriptionImpl(attribute),
                                              PsiModificationTracker.MODIFICATION_COUNT,
                                              externalResourceModificationTracker(attribute))
    );
  }

  private static @Nullable XmlAttributeDescriptor getDescriptionImpl(@NotNull XmlAttribute attribute) {
    final XmlTag tag = attribute.getParent();
    // e.g. XmlDecl or PI
    if (tag != null) {
      final XmlElementDescriptor descr = tag.getDescriptor();
      if (descr != null) {
        return descr.getAttributeDescriptor(attribute);
      }
    }
    return null;
  }

  private static @NotNull ModificationTracker externalResourceModificationTracker(@NotNull XmlAttribute attribute) {
    Project project = attribute.getProject();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    return () -> manager.getModificationCount(project);
  }

  private @Nullable Character getPreferredQuoteStyle() {
    final ASTNode value = XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(myAttribute.getNode());
    String currentValue = notNull(doIfNotNull(value, ASTNode::getText), "");
    return currentValue.startsWith("'") ? Character.valueOf('\'') : currentValue.startsWith("\"") ? Character.valueOf('"') : null;
  }

  void setValue(@NotNull String valueText) throws IncorrectOperationException {
    final ASTNode value = XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(myAttribute.getNode());
    final XmlAttribute attribute = createAttribute(StringUtil.defaultIfEmpty(myAttribute.getName(), "a"), valueText,
                                                   getPreferredQuoteStyle());
    final ASTNode newValue = XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(attribute.getNode());
    final ASTNode att = myAttribute.getNode();
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
  }

  protected XmlAttribute createAttribute(@NotNull String qname, @NotNull String value, @Nullable Character quoteStyle) {
    return XmlElementFactory.getInstance(myAttribute.getProject()).createAttribute(qname, value, quoteStyle, myAttribute);
  }

  @NotNull
  String getNamespace() {
    final String name = myAttribute.getName();
    final String prefixByQualifiedName = XmlUtil.findPrefixByQualifiedName(name);
    // The namespace name for an unprefixed attribute name always has no value. Namespace recommendation section 6.2, third paragraph
    if (prefixByQualifiedName.isEmpty()) return XmlUtil.EMPTY_URI;
    return myAttribute.getParent().getNamespaceByPrefix(prefixByQualifiedName);
  }

  static class VolatileState {
    final @NotNull String myDisplayText;
    final int @NotNull [] myGapDisplayStarts;
    final int @NotNull [] myGapPhysicalStarts;
    final @NotNull TextRange myValueTextRange; // text inside quotes, if there are any

    VolatileState(final @NotNull String displayText,
                  int @NotNull [] gapDisplayStarts,
                  int @NotNull [] gapPhysicalStarts,
                  @NotNull TextRange valueTextRange) {
      myDisplayText = displayText;
      myGapDisplayStarts = gapDisplayStarts;
      myGapPhysicalStarts = gapPhysicalStarts;
      myValueTextRange = valueTextRange;
    }
  }

  @Nullable
  XmlAttributeDelegate.VolatileState getFreshState() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    XmlAttributeDelegate.VolatileState state = myVolatileState;
    if (state == null) {
      state = recalculate();
    }
    return state;
  }

  protected void appendChildToDisplayValue(@NotNull StringBuilder buffer, @NotNull ASTNode child) {
    buffer.append(child.getChars());
  }

  private @Nullable XmlAttributeDelegate.VolatileState recalculate() {
    XmlAttributeValue value = myAttribute.getValueElement();
    if (value == null) return null;
    PsiElement firstChild = value.getFirstChild();
    if (firstChild == null) return null;
    ASTNode child = firstChild.getNode();
    TextRange valueTextRange = new TextRange(0, value.getTextLength());
    if (child != null && child.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      valueTextRange = new TextRange(child.getTextLength(), valueTextRange.getEndOffset());
      child = child.getTreeNext();
    }
    final IntList gapsStarts = new IntArrayList();
    final IntList gapsShifts = new IntArrayList();
    StringBuilder buffer = new StringBuilder(myAttribute.getTextLength());
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
        buffer.append(getEntityValue((XmlEntityRef)child));
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
      currentGapsSum += gapsShifts.getInt(i);
      gapDisplayStarts[i] = gapsStarts.getInt(i);
      gapPhysicalStarts[i] = gapDisplayStarts[i] + currentGapsSum;
    }
    final XmlAttributeDelegate.VolatileState
      volatileState = new XmlAttributeDelegate.VolatileState(buffer.toString(), gapDisplayStarts, gapPhysicalStarts, valueTextRange);
    myVolatileState = volatileState;
    return volatileState;
  }

  private static @NotNull String getEntityValue(final @NotNull XmlEntityRef entityRef) {
    final XmlEntityDecl decl = entityRef.resolve(entityRef.getContainingFile());
    if (decl != null) {
      final XmlAttributeValue valueElement = decl.getValueElement();
      if (valueElement != null) {
        return valueElement.getValue();
      }
    }
    return entityRef.getText();
  }

  PsiReference @NotNull [] getDefaultReferences(@NotNull PsiReferenceService.Hints hints) {
    //todo can it be moved to reference provider?
    if (PlatformUtils.isJetBrainsClient()) return PsiReference.EMPTY_ARRAY;

    if (hints.offsetInElement != null) {
      XmlElement nameElement = myAttribute.getNameElement();
      if (nameElement == null || hints.offsetInElement > nameElement.getStartOffsetInParent() + nameElement.getTextLength()) {
        return PsiReference.EMPTY_ARRAY;
      }
    }

    PsiReference[] referencesFromProviders = ReferenceProvidersRegistry.getReferencesFromProviders(myAttribute, hints);
    PsiReference[] refs;
    if (myAttribute.isNamespaceDeclaration()) {
      refs = new PsiReference[referencesFromProviders.length + 1];
      final String localName = myAttribute.getLocalName();
      final String prefix = XmlUtil.findPrefixByQualifiedName(myAttribute.getName());
      final TextRange range =
        prefix.isEmpty() ? TextRange.from(myAttribute.getName().length(), 0) : TextRange.from(prefix.length() + 1, localName.length());
      refs[0] = new SchemaPrefixReference(myAttribute, range, localName, null);
    }
    else {
      final String prefix = myAttribute.getNamespacePrefix();
      if (!prefix.isEmpty() && !myAttribute.getLocalName().isEmpty()) {
        refs = new PsiReference[referencesFromProviders.length + 2];
        XmlElement nameElement = myAttribute.getNameElement();
        TextRange prefixRange = TextRange.from(nameElement == null ? 0 : nameElement.getStartOffsetInParent(), prefix.length());
        refs[0] = new SchemaPrefixReference(myAttribute, prefixRange, prefix, null);
        refs[1] = new XmlAttributeReference(myAttribute);
      }
      else {
        refs = new PsiReference[referencesFromProviders.length + 1];
        refs[0] = new XmlAttributeReference(myAttribute);
      }
    }
    System.arraycopy(referencesFromProviders, 0, refs, refs.length - referencesFromProviders.length, referencesFromProviders.length);
    return refs;
  }

  int physicalToDisplay(int physicalIndex) {
    final XmlAttributeDelegate.VolatileState state = getFreshState();
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

  @NotNull
  PsiElement setName(final @NotNull String nameText) throws IncorrectOperationException {
    final ASTNode name = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(myAttribute.getNode());
    final String oldValue = notNull(myAttribute.getValue(), "");
    final XmlAttribute newAttribute = createAttribute(nameText, oldValue, getPreferredQuoteStyle());
    final ASTNode newName = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(newAttribute.getNode());
    if (!oldValue.isEmpty() && myAttribute.getLanguage().isKindOf(HTMLLanguage.INSTANCE)) {
      CodeEditUtil.replaceChild(myAttribute.getNode().getTreeParent(), myAttribute.getNode(), newAttribute.getNode());
      return newAttribute;
    }
    else if (name != null && newName != null) {
      CodeEditUtil.replaceChild(myAttribute.getNode(), name, newName);
    }
    return myAttribute;
  }

  int displayToPhysical(int displayIndex) {
    final XmlAttributeDelegate.VolatileState state = getFreshState();
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
}
