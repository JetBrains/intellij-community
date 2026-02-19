// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

/**
 * Allows providing bundled dictionaries.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/spell-checking.html#bundleddictionaryprovider">Spellchecking (IntelliJ Platform Docs)</a>
 * @see com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider
 */
public interface BundledDictionaryProvider {

  /**
   * @return Paths to dictionary files.
   */
  String[] getBundledDictionaries();
}
