// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlEntityDecl;

import java.util.Map;

public final class XmlEntityCache {
  public static void cacheParticularEntity(PsiFile file, XmlEntityDecl decl) {
    synchronized(XmlEntityCacheImplUtil.LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = XmlEntityCacheImplUtil.getCachingMap(file);
      final String name = decl.getName();
      if (cachingMap.containsKey(name)) return;
      final SmartPsiElementPointer declPointer = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(decl);

      cachingMap.put(
        name, CachedValuesManager.getManager(file.getProject()).createCachedValue(() -> {
          PsiElement declElement = declPointer.getElement();
          if (declElement instanceof XmlEntityDecl && declElement.isValid() && name.equals(((XmlEntityDecl)declElement).getName()))
            return new CachedValueProvider.Result<>((XmlEntityDecl)declElement, declElement);
          cachingMap.put(name,null);
          return new CachedValueProvider.Result<>(null, ModificationTracker.NEVER_CHANGED);
        },
                                                                                  false
      ));
    }
  }

  public static void copyEntityCaches(final PsiFile file, final PsiFile context) {
    synchronized (XmlEntityCacheImplUtil.LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = XmlEntityCacheImplUtil.getCachingMap(file);
      cachingMap.putAll(XmlEntityCacheImplUtil.getCachingMap(context));
    }

  }

  public static XmlEntityDecl getCachedEntity(PsiFile file, String name) {
    CachedValue<XmlEntityDecl> cachedValue;
    synchronized(XmlEntityCacheImplUtil.LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = XmlEntityCacheImplUtil.getCachingMap(file);
      cachedValue = cachingMap.get(name);
    }
    return cachedValue != null ? cachedValue.getValue():null;
  }
}
