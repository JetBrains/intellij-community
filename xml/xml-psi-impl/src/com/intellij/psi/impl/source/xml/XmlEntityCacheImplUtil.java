// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.xml.XmlEntityDecl;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class XmlEntityCacheImplUtil {
  public static final Object LOCK = new Object();

  private static final Key<Map<String,CachedValue<XmlEntityDecl>>> XML_ENTITY_DECL_MAP = Key.create("XML_ENTITY_DECL_MAP");

  public static Map<String, CachedValue<XmlEntityDecl>> getCachingMap(final PsiElement targetElement) {
    Map<String, CachedValue<XmlEntityDecl>> map = targetElement.getUserData(XML_ENTITY_DECL_MAP);
    if (map == null){
      map = new HashMap<>();
      targetElement.putUserData(XML_ENTITY_DECL_MAP, map);
    }
    return map;
  }
}
