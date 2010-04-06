/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
class AnchorReference implements PsiReference, EmptyResolveMessageProvider {
  private final String myAnchor;
  private final PsiReference myFileReference;
  private final PsiElement myElement;
  private final int myOffset;
  private final boolean mySoft;
  @NonNls
  private static final String ANCHOR_ELEMENT_NAME = "a";
  private static final String MAP_ELEMENT_NAME = "map";
  private static final Key<CachedValue<Map<String,XmlTag>>> ourCachedIdsKey = Key.create("cached.ids");

  AnchorReference(final String anchor, @Nullable final FileReference psiReference, final PsiElement element, final int offset,
                  final boolean soft) {

    myAnchor = anchor;
    myFileReference = psiReference;
    myElement = element;
    myOffset = offset;
    mySoft = soft;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return new TextRange(myOffset,myOffset+myAnchor.length());
  }

  public PsiElement resolve() {
    if (myAnchor.length() == 0) {
      return myElement;
    }
    Map<String,XmlTag> map = getIdMap();
    final XmlTag tag = map != null ? map.get(myAnchor):null;
    if (tag != null) {
      XmlAttribute attribute = tag.getAttribute("id", null);
      if (attribute==null) attribute = tag.getAttribute("name",null);

      if (attribute == null && MAP_ELEMENT_NAME.equalsIgnoreCase(tag.getName())) {
        attribute = tag.getAttribute("usemap", null);
      }

      return attribute.getValueElement();
    }

    return null;
  }

  private static boolean processXmlElements(XmlTag element, PsiElementProcessor processor) {
    if (!_processXmlElements(element,processor)) return false;

    for(PsiElement next = element.getNextSibling(); next != null; next = next.getNextSibling()) {
      if (next instanceof XmlTag) {
        if (!_processXmlElements((XmlTag)next,processor)) return false;
      }
    }

    return true;
  }

  static boolean _processXmlElements(XmlTag element, PsiElementProcessor processor) {
    if (!processor.execute(element)) return false;
    final XmlTag[] subTags = element.getSubTags();

    for (int i = 0; i < subTags.length; i++) {
      if(!_processXmlElements(subTags[i],processor)) return false;
    }

    return true;
  }

  private Map<String,XmlTag> getIdMap() {
    final XmlFile file = getFile();

    if (file != null) {
      CachedValue<Map<String, XmlTag>> value = file.getUserData(ourCachedIdsKey);
      if (value == null) {
        value = CachedValuesManager.getManager(file.getProject()).createCachedValue(new CachedValueProvider<Map<String, XmlTag>>() {
          public Result<Map<String, XmlTag>> compute() {
            final Map<String,XmlTag> resultMap = new HashMap<String, XmlTag>();
            XmlDocument document = HtmlUtil.getRealXmlDocument(file.getDocument());
            final XmlTag rootTag = document != null ? document.getRootTag():null;
            
            if (rootTag != null) {
              processXmlElements(rootTag,
                new PsiElementProcessor() {
                  public boolean execute(final PsiElement element) {
                    final String anchorValue = element instanceof XmlTag ? getAnchorValue((XmlTag)element):null;

                    if (anchorValue!=null) {
                      resultMap.put(anchorValue, (XmlTag)element);
                    }
                    return true;
                  }
                }
              );
            }
            return new Result<Map<String, XmlTag>>(resultMap, file);
          }
        }, false);
        file.putUserData(ourCachedIdsKey, value);
      }

      return value.getValue();
    }
    return null;
  }

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

  public String getCanonicalText() {
    return myAnchor;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return ElementManipulators.getManipulator(myElement).handleContentChange(
      myElement,
      getRangeInElement(),
      newElementName
    );
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof XmlAttributeValue)) return false;
    return myElement.getManager().areElementsEquivalent(element,resolve());
  }

  @NotNull
  public Object[] getVariants() {
    final Map<String, XmlTag> idMap = getIdMap();
    if (idMap == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    return idMap.keySet().toArray(new Object[idMap.size()]);
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

  public boolean isSoft() {
    return mySoft;
  }

  public String getUnresolvedMessagePattern() {
    final XmlFile xmlFile = getFile();
    return xmlFile == null ? 
           XmlBundle.message("cannot.resolve.anchor", myAnchor) :
           XmlBundle.message("cannot.resolve.anchor.in.file", myAnchor, xmlFile.getName());
  }
}
