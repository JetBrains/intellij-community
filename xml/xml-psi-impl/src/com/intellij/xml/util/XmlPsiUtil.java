// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlEntityCache;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.IdempotenceChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class XmlPsiUtil {
  private static final Key<CachedValue<PsiElement>> PARSED_DECL_KEY = Key.create("PARSED_DECL_KEY");
  @NonNls public static final String XINCLUDE_URI = "http://www.w3.org/2001/XInclude";

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor<? super PsiElement> processor, boolean deepFlag) {
    return processXmlElements(element, processor, deepFlag, false);
  }

  public static boolean processXmlElements(XmlElement element,
                                           PsiElementProcessor<? super PsiElement> processor,
                                           boolean deepFlag,
                                           boolean wideFlag) {
    if (element == null) return true;
    PsiFile baseFile = element.isValid() ? element.getContainingFile() : null;
    return processXmlElements(element, processor, deepFlag, wideFlag, baseFile);
  }

  public static boolean processXmlElements(final XmlElement element,
                                           final PsiElementProcessor<? super PsiElement> processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile) {
    return processXmlElements(element, processor, deepFlag, wideFlag, baseFile, true);
  }

  public static boolean processXmlElements(final XmlElement element,
                                           final PsiElementProcessor<? super PsiElement> processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile,
                                           boolean processIncludes) {
    return AstLoadingFilter.forceAllowTreeLoading(baseFile, () ->
      new XmlElementProcessor(baseFile, processor).processXmlElements(element, deepFlag, wideFlag, processIncludes)
    );
  }

  public static boolean processXmlElementChildren(final XmlElement element,
                                                  final PsiElementProcessor<? super PsiElement> processor,
                                                  final boolean deepFlag) {
    final XmlPsiUtil.XmlElementProcessor p = new XmlPsiUtil.XmlElementProcessor(element.getContainingFile(), processor);

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!p.processElement(child, deepFlag, false, true)) return false;
    }

    return true;
  }

  @Nullable
  public static XmlElement findElement(@NotNull final XmlElement parent, @NotNull final IElementType.Predicate predicate) {
    final Ref<XmlElement> result = new Ref<>();
    parent.processElements(new PsiElementProcessor<>() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        if (element instanceof XmlElement && predicate.matches(element.getNode().getElementType())) {
          result.set((XmlElement)element);
          return false;
        }
        return true;
      }
    }, parent);

    return result.get();
  }

  private static class XmlElementProcessor {
    private final PsiElementProcessor<? super PsiElement> processor;
    private final PsiFile targetFile;
    private final Set<String> visitedEntities = new HashSet<>();

    XmlElementProcessor(PsiFile _targetFile, @NotNull PsiElementProcessor<? super PsiElement> _processor) {
      processor = _processor;
      targetFile = _targetFile;
    }

    private boolean processXmlElements(PsiElement element, boolean deepFlag, boolean wideFlag, boolean processIncludes) {
      if (deepFlag) if (!processor.execute(element)) return false;

      PsiElement startFrom = element.getFirstChild();

      if (element instanceof XmlEntityRef ref) {
        if (!visitedEntities.add(ref.getText())) return true;
        PsiElement newElement = parseEntityRef(targetFile, ref);

        while (newElement != null) {
          if (!processElement(newElement, deepFlag, wideFlag, processIncludes)) return false;
          newElement = newElement.getNextSibling();
        }

        return true;
      }
      else if (element instanceof XmlConditionalSection xmlConditionalSection) {
        if (!xmlConditionalSection.isIncluded(targetFile)) return true;
        startFrom = xmlConditionalSection.getBodyStart();
      }
      else if (processIncludes && XmlIncludeHandler.isXInclude(element)) {
        if (IdempotenceChecker.isLoggingEnabled()) {
          IdempotenceChecker.logTrace("Processing xinclude " + element.getText());
        }
        PsiElement[] tags = InclusionProvider.getIncludedTags((XmlTag)element);
        for (PsiElement psiElement : tags) {
          if (IdempotenceChecker.isLoggingEnabled()) {
            IdempotenceChecker.logTrace("Processing included tag " + psiElement);
          }
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
          if (!processXmlElements(child, false, wideFlag, true)) return false;
        }
        else if (!processor.execute(child)) return false;
      }
      if (targetFile != null && child instanceof XmlEntityDecl xmlEntityDecl) {
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
      if (e instanceof PsiFile refFile) {
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
        if (res == null) return new CachedValueProvider.Result<>(null, targetFile);
        if (!entityDecl.isInternalReference()) XmlEntityCache.copyEntityCaches(res.getContainingFile(), targetFile);
        return new CachedValueProvider.Result<>(res, res.getUserData(XmlElement.DEPENDING_ELEMENT), entityDecl, targetFile, entityRef);
      }, false);
      value = ((XmlEntityRefImpl)entityRef).putUserDataIfAbsent(PARSED_DECL_KEY, value);
    }

    return value.getValue();
  }
}
