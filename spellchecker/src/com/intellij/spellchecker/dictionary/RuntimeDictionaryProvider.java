// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Extension point to provide a spellchecker with additional non file-based dictionaries.
 *
 * Unlike {@link CustomDictionaryProvider} and {@link com.intellij.spellchecker.BundledDictionaryProvider}
 * that require {@link Dictionary} to have a file representation {@link RuntimeDictionaryProvider} supports
 * non file-based dictionaries.
 *
 * It means that dictionary may be even generated at a runtime, or downloaded from remote server.
 *
 * Dictionaries returned from this extension point will be used by a spellchecker as ordinary
 * dictionaries. It means that all operations in those dictionaries must work as fast as work
 * operations in local dictionaries.
 *
 * Note, that dictionary must have a human-readable name, since it is represented by name in a UI.
 */
public interface RuntimeDictionaryProvider {
  ExtensionPointName<RuntimeDictionaryProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.spellchecker.dictionary.runtimeDictionaryProvider");

  Dictionary[] getDictionaries();
}
