/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final Key<CachedValue<Map<String,XmlTag>>> ourCachedIdsKey = Key.create("cached.ids");

  AnchorReferenceImpl(final String anchor, @Nullable final FileReference psiReference, final PsiElement element, final int offset,
                      final boolean soft) {

    myAnchor = anchor;
    myFileReference = psiReference;
    myElement = element;
    myOffset = offset;
    mySoft = soft;
  }

  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  public TextRange getRangeInElement() {
    return new TextRange(myOffset,myOffset+myAnchor.length());
  }

  @Override
  public PsiElement resolve() {
    if (myAnchor.isEmpty()) {
      return myElement;
    }
    Map<String,XmlTag> map = getIdMap();
    final XmlTag tag = map != null ? map.get(myAnchor):null;
    if (tag != null) {
      XmlAttribute attribute = tag.getAttribute("id");
      if (attribute==null) attribute = tag.getAttribute("name");

      if (attribute == null && MAP_ELEMENT_NAME.equalsIgnoreCase(tag.getName())) {
        attribute = tag.getAttribute("usemap");
      }

      assert attribute != null: tag.getText();
      return attribute.getValueElement();
    }

    return null;
  }

  private static boolean processXmlElements(XmlTag element, PsiElementProcessor<XmlTag> processor) {
    if (!_processXmlElements(element,processor)) return false;

    for(PsiElement next = element.getNextSibling(); next != null; next = next.getNextSibling()) {
      if (next instanceof XmlTag) {
        if (!_processXmlElements((XmlTag)next,processor)) return false;
      }
    }

    return true;
  }

  static boolean _processXmlElements(XmlTag element, PsiElementProcessor<XmlTag> processor) {
    if (!processor.execute(element)) return false;
    final XmlTag[] subTags = element.getSubTags();

    for (XmlTag subTag : subTags) {
      if (!_processXmlElements(subTag, processor)) return false;
    }

    return true;
  }

  @Nullable
  private Map<String,XmlTag> getIdMap() {
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

    if (attributeValue!=null) {
      return attributeValue;
    }

    if (ANCHOR_ELEMENT_NAME.equalsIgnoreCase(xmlTag.getName())) {
      final String attributeValue2 = xmlTag.getAttributeValue("name");
      if (attributeValue2!=null) {
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
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return ElementManipulators.getManipulator(myElement).handleContentChange(
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
  public boolean isReferenceTo(PsiElement element) {
    return element instanceof XmlAttributeValue && myElement.getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    final Map<String, XmlTag> idMap = getIdMap();
    if (idMap == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    String[] variants = idMap.keySet().toArray(new String[idMap.size()]);
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
      return psiElement instanceof XmlFile ? (XmlFile)psiElement:null;
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
           XmlBundle.message("cannot.resolve.anchor", myAnchor) :
           XmlBundle.message("cannot.resolve.anchor.in.file", myAnchor, xmlFile.getName());
  }

  // separate static class to avoid memory leak via this$0
  private static class MapCachedValueProvider implements CachedValueProvider<Map<String, XmlTag>> {
    private final XmlFile myFile;

    public MapCachedValueProvider(XmlFile file) {
      myFile = file;
    }

    @Override
    public Result<Map<String, XmlTag>> compute() {
      final Map<String,XmlTag> resultMap = new HashMap<>();
      XmlDocument document = HtmlUtil.getRealXmlDocument(myFile.getDocument());
      final XmlTag rootTag = document != null ? document.getRootTag():null;

      if (rootTag != null) {
        processXmlElements(rootTag,
          new PsiElementProcessor<XmlTag>() {
            @Override
            public boolean execute(@NotNull final XmlTag element) {
              final String anchorValue = getAnchorValue(element);

              if (anchorValue!=null) {
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
