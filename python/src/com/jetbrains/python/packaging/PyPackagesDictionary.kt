// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider
import com.jetbrains.python.PyBundle.message

/**
 * Ignores the names of Python packages in spellcheck.
 */
class PyPackagesDictionary : Dictionary {
  override fun getName(): String = message("python.packages.dictionary.name")

  override fun contains(word: String): Boolean = PyPIPackageCache.getInstance().containsPackage(word)

  override fun getWords(): MutableSet<String> = throw UnsupportedOperationException()

  class PyPackagesDictionaryProvider : RuntimeDictionaryProvider {
    override fun getDictionaries(): Array<Dictionary> = arrayOf(PyPackagesDictionary())
  }
}