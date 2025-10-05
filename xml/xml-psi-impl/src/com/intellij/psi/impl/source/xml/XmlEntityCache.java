// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlEntityDecl;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

public final class XmlEntityCache {
  @ApiStatus.Internal
  public static final Object LOCK = new Object();
  private static final Key<Map<String,CachedValue<XmlEntityDecl>>> XML_ENTITY_DECL_MAP = Key.create("XML_ENTITY_DECL_MAP");

  public static void cacheParticularEntity(PsiFile file, XmlEntityDecl decl) {
    synchronized(LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = getCachingMap(file);
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

  @ApiStatus.Internal
  public static Map<String, CachedValue<XmlEntityDecl>> getCachingMap(final PsiElement targetElement) {
    Map<String, CachedValue<XmlEntityDecl>> map = targetElement.getUserData(XML_ENTITY_DECL_MAP);
    if (map == null){
      map = new HashMap<>();
      targetElement.putUserData(XML_ENTITY_DECL_MAP, map);
    }
    return map;
  }

  public static void copyEntityCaches(final PsiFile file, final PsiFile context) {
    synchronized (LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = getCachingMap(file);
      cachingMap.putAll(getCachingMap(context));
    }

  }

  public static XmlEntityDecl getCachedEntity(PsiFile file, String name) {
    CachedValue<XmlEntityDecl> cachedValue;
    synchronized(LOCK) {
      final Map<String, CachedValue<XmlEntityDecl>> cachingMap = getCachingMap(file);
      cachedValue = cachingMap.get(name);
    }
    return cachedValue != null ? cachedValue.getValue():null;
  }
}
