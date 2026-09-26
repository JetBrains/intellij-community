// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.components.service
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.pip.PyPiPackageCache
import org.jetbrains.annotations.ApiStatus

private val ALPHABET_REGEX = Regex("[-a-z0-9]+")

/**
 * Ignores the names of Python packages in spellcheck.
 */
@ApiStatus.Internal
class PyPackagesDictionary : Dictionary {
  override fun getName(): String = message("python.packages.dictionary.name")

  override fun lookup(word: String): LookupStatus {
    if (word in service<PyPiPackageCache>()) return LookupStatus.Present
    if (!ALPHABET_REGEX.matches(word.lowercase())) return LookupStatus.Alien
    return LookupStatus.Absent
  }

  override fun getWords(): MutableSet<String> = throw UnsupportedOperationException()

  class PyPackagesDictionaryProvider : RuntimeDictionaryProvider {
    override fun getDictionaries(): Array<Dictionary> = arrayOf(PyPackagesDictionary())
  }
}