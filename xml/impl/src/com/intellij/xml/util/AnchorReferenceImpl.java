// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class AnchorReferenceImpl implements AnchorReference, PsiReference, EmptyResolveMessageProvider {
  private final String myAnchor;
  private final FileReference myFileReference;
  private final PsiElement myElement;
  private final int myOffset;
  private final boolean mySoft;
  @NonNls
  private static final String ANCHOR_ELEMENT_NAME = "a";
  @NonNls private static final String MAP_ELEMENT_NAME = "map";
  private static final Key<CachedValue<Map<String, XmlTag>>> ourCachedIdsKey = Key.create("cached.ids");

  AnchorReferenceImpl(final String anchor, @Nullable final FileReference psiReference, @NotNull final PsiElement element, final int offset,
                      final boolean soft) {

    myAnchor = anchor;
    myFileReference = psiReference;
    myElement = element;
    myOffset = offset;
    mySoft = soft;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return new TextRange(myOffset, myOffset + myAnchor.length());
  }

  @Override
  public PsiElement resolve() {
    if (myAnchor.isEmpty()) {
      return myElement;
    }
    Map<String, XmlTag> map = getIdMap();
    final XmlTag tag = map != null ? map.get(myAnchor) : null;
    if (tag != null) {
      XmlAttribute attribute = tag.getAttribute("id");
      if (attribute == null) attribute = tag.getAttribute("name");

      if (attribute == null && MAP_ELEMENT_NAME.equalsIgnoreCase(tag.getName())) {
        attribute = tag.getAttribute("usemap");
      }

      assert attribute != null : tag.getText();
      return attribute.getValueElement();
    }

    return null;
  }

  private static boolean processXmlElements(XmlTag element, PsiElementProcessor<? super XmlTag> processor) {
    if (!_processXmlElements(element, processor)) return false;

    for (PsiElement next = element.getNextSibling(); next != null; next = next.getNextSibling()) {
      if (next instanceof XmlTag) {
        if (!_processXmlElements((XmlTag)next, processor)) return false;
      }
    }

    return true;
  }

  static boolean _processXmlElements(XmlTag element, PsiElementProcessor<? super XmlTag> processor) {
    if (!processor.execute(element)) return false;
    final XmlTag[] subTags = element.getSubTags();

    for (XmlTag subTag : subTags) {
      if (!_processXmlElements(subTag, processor)) return false;
    }

    return true;
  }

  @Nullable
  private Map<String, XmlTag> getIdMap() {
    final XmlFile file = getFile();

    if (file != null) {
      CachedValue<Map<String, XmlTag>> value = file.getUserData(ourCachedIdsKey);
      if (value == null) {
        value = CachedValuesManager.getManager(file.getProject()).createCachedValue(new MapCachedValueProvider(file), false);
        file.putUserData(ourCachedIdsKey, value);
      }

      return value.getValue();
    }
    return null;
  }

  @Nullable
  private static String getAnchorValue(final XmlTag xmlTag) {
    final String attributeValue = xmlTag.getAttributeValue("id");

    if (attributeValue != null) {
      return attributeValue;
    }

    if (ANCHOR_ELEMENT_NAME.equalsIgnoreCase(xmlTag.getName())) {
      final String attributeValue2 = xmlTag.getAttributeValue("name");
      if (attributeValue2 != null) {
        return attributeValue2;
      }
    }

    if (MAP_ELEMENT_NAME.equalsIgnoreCase(xmlTag.getName())) {
      final String map_anchor = xmlTag.getAttributeValue("name");
      if (map_anchor != null) {
        return map_anchor;
      }
    }

    return null;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myAnchor;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return ElementManipulators.handleContentChange(
      myElement,
      getRangeInElement(),
      newElementName
    );
  }

  @Override
  @Nullable
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return element instanceof XmlAttributeValue && myElement.getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public Object @NotNull [] getVariants() {
    final Map<String, XmlTag> idMap = getIdMap();
    if (idMap == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

    String[] variants = ArrayUtilRt.toStringArray(idMap.keySet());
    LookupElement[] elements = new LookupElement[variants.length];
    for (int i = 0, variantsLength = variants.length; i < variantsLength; i++) {
      elements[i] = LookupElementBuilder.create(variants[i]).withCaseSensitivity(true);
    }
    return elements;
  }

  @Nullable
  private XmlFile getFile() {
    if (myFileReference != null) {
      final PsiElement psiElement = myFileReference.resolve();
      return psiElement instanceof XmlFile ? (XmlFile)psiElement : null;
    }

    final PsiFile containingFile = myElement.getContainingFile();
    if (containingFile instanceof XmlFile) {
      return (XmlFile)containingFile;
    }
    else {
      final XmlExtension extension = XmlExtension.getExtensionByElement(myElement);
      return extension == null ? null : extension.getContainingFile(myElement);
    }
  }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  @Override
  @NotNull
  public String getUnresolvedMessagePattern() {
    final XmlFile xmlFile = getFile();
    return xmlFile == null ?
           XmlBundle.message("xml.inspections.cannot.resolve.anchor", myAnchor) :
           XmlBundle.message("xml.inspections.cannot.resolve.anchor.in.file", myAnchor, xmlFile.getName());
  }

  // separate static class to avoid memory leak via this$0
  private static class MapCachedValueProvider implements CachedValueProvider<Map<String, XmlTag>> {
    private final XmlFile myFile;

    MapCachedValueProvider(XmlFile file) {
      myFile = file;
    }

    @Override
    public Result<Map<String, XmlTag>> compute() {
      final Map<String, XmlTag> resultMap = new HashMap<>();
      XmlDocument document = HtmlUtil.getRealXmlDocument(myFile.getDocument());
      final XmlTag rootTag = document != null ? document.getRootTag() : null;

      if (rootTag != null) {
        processXmlElements(
          rootTag, new PsiElementProcessor<>() {
            @Override
            public boolean execute(@NotNull final XmlTag element) {
              final String anchorValue = getAnchorValue(element);

              if (anchorValue != null) {
                resultMap.put(anchorValue, element);
              }
              return true;
            }
          }
        );
      }
      return new Result<>(resultMap, myFile);
    }
  }
}
