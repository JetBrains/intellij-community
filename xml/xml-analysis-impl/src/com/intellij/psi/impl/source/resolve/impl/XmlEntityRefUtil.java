// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.impl;

import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.xml.XmlEntityCache;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class XmlEntityRefUtil {
  private static final @NonNls String GT_ENTITY = "&gt;";
  private static final @NonNls String QUOT_ENTITY = "&quot;";

  public static @Nullable XmlEntityDecl resolveEntity(XmlEntityRef element, PsiFile targetFile) {
    String text = element.getText();
    if (text.equals(GT_ENTITY) || text.equals(QUOT_ENTITY)) return null;
    return resolveEntity(element, text, targetFile);
  }

  public static XmlEntityDecl resolveEntity(final XmlElement element, final String text, PsiFile targetFile) {
    final String entityName = text.substring(1, text.length() - 1);

    final PsiElement targetElement = targetFile != null ? targetFile : element;
    CachedValue<XmlEntityDecl> value;
    synchronized (XmlEntityCache.LOCK) {
      Map<String, CachedValue<XmlEntityDecl>> map = XmlEntityCache.getCachingMap(targetElement);

      value = map.get(entityName);
      final PsiFile containingFile = element.getContainingFile();

      if (value == null) {
        final PsiManager manager = element.getManager();
        if (manager == null) {
          return doResolveEntity(targetElement, entityName, containingFile).getValue();
        }
        value = CachedValuesManager.getManager(manager.getProject()).createCachedValue(
          () -> doResolveEntity(targetElement, entityName, containingFile), true);


        map.put(entityName, value);
      }
    }
    return value.getValue();
  }

  private static CachedValueProvider.Result<XmlEntityDecl> doResolveEntity(final PsiElement targetElement,
                                                                           final String entityName,
                                                                           final PsiFile contextFile) {
    return RecursionManager.doPreventingRecursion(targetElement, true, new Computable<>() {
      @Override
      public CachedValueProvider.Result<XmlEntityDecl> compute() {
        final List<PsiElement> deps = new ArrayList<>();
        final XmlEntityDecl[] result = {null};

        PsiElementProcessor<PsiElement> processor = new PsiElementProcessor<>() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            if (element instanceof XmlDoctype xmlDoctype) {
              final String dtdUri = getDtdForEntity(xmlDoctype);
              if (dtdUri != null) {
                XmlFile file = XmlUtil.getContainingFile(element);
                if (file == null) return true;
                final XmlFile xmlFile = XmlUtil.findNamespace(file, dtdUri);
                if (xmlFile != null) {
                  if (xmlFile != targetElement) {
                    deps.add(xmlFile);
                    if (!XmlUtil.processXmlElements(xmlFile, this, true)) return false;
                  }
                }
              }
              final XmlMarkupDecl markupDecl = xmlDoctype.getMarkupDecl();
              if (markupDecl != null) {
                if (!XmlUtil.processXmlElements(markupDecl, this, true)) return false;
              }
            }
            else if (element instanceof XmlEntityDecl entityDecl) {
              final String declName = entityDecl.getName();
              if (StringUtil.equals(declName, entityName)) {
                result[0] = entityDecl;
                deps.add(entityDecl.getContainingFile());
                return false;
              }
            }

            return true;
          }
        };
        FileViewProvider provider = targetElement.getContainingFile().getViewProvider();
        deps.add(provider.getPsi(provider.getBaseLanguage()));

        boolean notfound = PsiTreeUtil.processElements(targetElement, processor);
        if (notfound) {
          if (contextFile != targetElement && contextFile != null && contextFile.isValid()) {
            notfound = PsiTreeUtil.processElements(contextFile, processor);
          }
        }

        if (notfound &&       // no dtd ref at all
            targetElement instanceof XmlFile &&
            deps.size() == 1 &&
            ((XmlFile)targetElement).getFileType() != DTDFileType.INSTANCE
        ) {
          for (XmlFile descriptorFile : XmlExtension.getExtension((XmlFile)targetElement).getCharEntitiesDTDs((XmlFile)targetElement)) {
            if (!descriptorFile.getName().equals(((XmlFile)targetElement).getName() + ".dtd")) {
              deps.add(descriptorFile);
              if (!XmlUtil.processXmlElements(descriptorFile, processor, true)) {
                break;
              }
            }
          }
        }
        return new CachedValueProvider.Result<>(result[0], ArrayUtil.toObjectArray(deps));
      }
    });
  }

  private static String getDtdForEntity(XmlDoctype xmlDoctype) {
    return HtmlUtil.isHtml5Doctype(xmlDoctype) ? Html5SchemaProvider.getCharsDtdLocation() : XmlUtil.getDtdUri(xmlDoctype);
  }
}
