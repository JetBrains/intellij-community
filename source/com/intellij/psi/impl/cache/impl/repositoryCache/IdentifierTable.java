/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.cache.impl.repositoryCache;

import gnu.trove.THashMap;

/**
 * @author max
 */
public class IdentifierTable {
    private THashMap<String, String> myMap = new THashMap<String, String>(10, 0.9f);

    public String intern(String name) {
        String entry = myMap.get(name);
        if (entry == null) {
            myMap.put(name, name);
            entry = name;
        }
        return entry;
    }
}
