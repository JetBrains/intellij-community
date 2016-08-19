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
package com.intellij.xml.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlEntityCache;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;

public class XmlPsiUtil {
  private static final Key<CachedValue<PsiElement>> PARSED_DECL_KEY = Key.create("PARSED_DECL_KEY");
  @NonNls public static final String XINCLUDE_URI = "http://www.w3.org/2001/XInclude";

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor processor, boolean deepFlag) {
    return processXmlElements(element, processor, deepFlag, false);
  }

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor processor, boolean deepFlag, boolean wideFlag) {
    if (element == null) return true;
    PsiFile baseFile = element.isValid() ? element.getContainingFile() : null;
    return processXmlElements(element, processor, deepFlag, wideFlag, baseFile);
  }

  public static boolean processXmlElements(final XmlElement element,
                                           final PsiElementProcessor processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile) {
    return processXmlElements(element, processor, deepFlag, wideFlag, baseFile, true);
  }

  public static boolean processXmlElements(final XmlElement element,
                                           final PsiElementProcessor processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile,
                                           boolean processIncludes) {
    return new XmlElementProcessor(processor, baseFile).processXmlElements(element, deepFlag, wideFlag, processIncludes);
  }

  public static boolean processXmlElementChildren(final XmlElement element, final PsiElementProcessor processor, final boolean deepFlag) {
    final XmlPsiUtil.XmlElementProcessor p = new XmlPsiUtil.XmlElementProcessor(processor, element.getContainingFile());

    final boolean wideFlag = false;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!p.processElement(child, deepFlag, wideFlag, true) && !wideFlag) return false;
    }

    return true;
  }

  private static class XmlElementProcessor {
    private final PsiElementProcessor processor;
    private final PsiFile targetFile;

    XmlElementProcessor(PsiElementProcessor _processor, PsiFile _targetFile) {
      processor = _processor;
      targetFile = _targetFile;
    }

    private boolean processXmlElements(PsiElement element, boolean deepFlag, boolean wideFlag, boolean processIncludes) {
      if (deepFlag) if (!processor.execute(element)) return false;

      PsiElement startFrom = element.getFirstChild();

      if (element instanceof XmlEntityRef) {
        XmlEntityRef ref = (XmlEntityRef)element;

        PsiElement newElement = parseEntityRef(targetFile, ref);

        while (newElement != null) {
          if (!processElement(newElement, deepFlag, wideFlag, processIncludes)) return false;
          newElement = newElement.getNextSibling();
        }

        return true;
      }
      else if (element instanceof XmlConditionalSection) {
        XmlConditionalSection xmlConditionalSection = (XmlConditionalSection)element;
        if (!xmlConditionalSection.isIncluded(targetFile)) return true;
        startFrom = xmlConditionalSection.getBodyStart();
      }
      else if (processIncludes && XmlIncludeHandler.isXInclude(element)) {
        for (PsiElement psiElement : InclusionProvider.getIncludedTags((XmlTag)element)) {
          if (!processElement(psiElement, deepFlag, wideFlag, true)) return false;
        }
      }

      for (PsiElement child = startFrom; child != null; child = child.getNextSibling()) {
        if (!processElement(child, deepFlag, wideFlag, processIncludes) && !wideFlag) return false;
      }

      return true;
    }

    private boolean processElement(PsiElement child, boolean deepFlag, boolean wideFlag, boolean processIncludes) {
      if (deepFlag) {
        if (!processXmlElements(child, true, wideFlag, processIncludes)) {
          return false;
        }
      }
      else {
        if (child instanceof XmlEntityRef) {
          if (!processXmlElements(child, false, wideFlag, processIncludes)) return false;
        }
        else if (child instanceof XmlConditionalSection) {
          if (!processXmlElements(child, false, wideFlag, processIncludes)) return false;
        }
        else if (processIncludes && XmlIncludeHandler.isXInclude(child)) {
          if (!processXmlElements(child, false, wideFlag, processIncludes)) return false;
        }
        else if (!processor.execute(child)) return false;
      }
      if (targetFile != null && child instanceof XmlEntityDecl) {
        XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)child;
        XmlEntityCache.cacheParticularEntity(targetFile, xmlEntityDecl);
      }
      return true;
    }
  }

  private static PsiElement parseEntityRef(PsiFile targetFile, XmlEntityRef ref) {
    XmlEntityDecl.EntityContextType type = getContextType(ref);

    {
      final XmlEntityDecl entityDecl = ref.resolve(targetFile);
      if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, ref);
    }

    PsiElement e = ref;
    while (e != null) {
      if (e.getUserData(XmlElement.INCLUDING_ELEMENT) != null) {
        e = e.getUserData(XmlElement.INCLUDING_ELEMENT);
        final PsiFile f = e.getContainingFile();
        if (f != null) {
          final XmlEntityDecl entityDecl = ref.resolve(targetFile);
          if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, ref);
        }

        continue;
      }
      if (e instanceof PsiFile) {
        PsiFile refFile = (PsiFile)e;
        final XmlEntityDecl entityDecl = ref.resolve(refFile);
        if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, ref);
        break;
      }

      e = e.getParent();
    }

    final PsiElement element = ref.getUserData(XmlElement.DEPENDING_ELEMENT);
    if (element instanceof XmlFile) {
      final XmlEntityDecl entityDecl = ref.resolve((PsiFile)element);
      if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, ref);
    }

    return null;
  }

  private static XmlEntityDecl.EntityContextType getContextType(XmlEntityRef ref) {
    XmlEntityDecl.EntityContextType type = XmlEntityDecl.EntityContextType.GENERIC_XML;
    PsiElement temp = ref;
    while (temp != null) {
      if (temp instanceof XmlAttributeDecl) {
        type = XmlEntityDecl.EntityContextType.ATTRIBUTE_SPEC;
      }
      else if (temp instanceof XmlElementDecl) {
        type = XmlEntityDecl.EntityContextType.ELEMENT_CONTENT_SPEC;
      }
      else if (temp instanceof XmlAttlistDecl) {
        type = XmlEntityDecl.EntityContextType.ATTLIST_SPEC;
      }
      else if (temp instanceof XmlEntityDecl) {
        type = XmlEntityDecl.EntityContextType.ENTITY_DECL_CONTENT;
      }
      else if (temp instanceof XmlEnumeratedType) {
        type = XmlEntityDecl.EntityContextType.ENUMERATED_TYPE;
      }
      else if (temp instanceof XmlAttributeValue) {
        type = XmlEntityDecl.EntityContextType.ATTR_VALUE;
      }
      else {
        temp = temp.getContext();
        continue;
      }
      break;
    }
    return type;
  }

  private static PsiElement parseEntityDecl(final XmlEntityDecl entityDecl,
                                            final PsiFile targetFile,
                                            final XmlEntityDecl.EntityContextType type,
                                            final XmlEntityRef entityRef) {
    CachedValue<PsiElement> value = entityRef.getUserData(PARSED_DECL_KEY);

    if (value == null) {
      value = CachedValuesManager.getManager(entityDecl.getProject()).createCachedValue(() -> {
        final PsiElement res = entityDecl.parse(targetFile, type, entityRef);
        if (res == null) return new CachedValueProvider.Result<>(res, targetFile);
        if (!entityDecl.isInternalReference()) XmlEntityCache.copyEntityCaches(res.getContainingFile(), targetFile);
        return new CachedValueProvider.Result<>(res, res.getUserData(XmlElement.DEPENDING_ELEMENT), entityDecl, targetFile, entityRef);
      }, false);
      value = ((XmlEntityRefImpl)entityRef).putUserDataIfAbsent(PARSED_DECL_KEY, value);
    }

    return value.getValue();
  }
}
