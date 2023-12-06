// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.hunspell

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.ui.SpellCheckingNotifier
import com.intellij.spellchecker.util.SpellCheckerBundle
import java.io.FileNotFoundException
import java.text.ParseException

internal class HunspellDictionaryProvider : CustomDictionaryProvider {
  private val UNSUPPORTED_LANGUAGES = setOf("hu_HU")

  private fun isHunspellPluginInstalled(): Boolean {
    val hunspellId = PluginId.getId("hunspell")
    val ideaPluginDescriptor = PluginManagerCore.getPlugin(hunspellId)
    return PluginManagerCore.isPluginInstalled(hunspellId) && ideaPluginDescriptor != null && ideaPluginDescriptor.isEnabled
  }

  override fun get(path: String): Dictionary? {
    try {
      val dict = HunspellDictionary(path)

      if (dict.language() in UNSUPPORTED_LANGUAGES) {
        if (!isHunspellPluginInstalled()) {
          SpellCheckingNotifier.showWarningNotificationBalloon(SpellCheckerBundle.message("dictionary.unsupported.language.title"),
                                                               SpellCheckerBundle.message("dictionary.unsupported.language", path))
        }

        return null
      }

      return dict
    }
    catch (e: FileNotFoundException) {
      SpellCheckingNotifier.showWarningNotificationBalloon(SpellCheckerBundle.message("dictionary.not.found.title"),
                                                           SpellCheckerBundle.message("dictionary.not.found", path))
    }
    catch (e: ParseException) {
      SpellCheckingNotifier.showWarningNotificationBalloon(SpellCheckerBundle.message("dictionary.unsupported.format.title"),
                                                           SpellCheckerBundle.message("dictionary.unsupported.format", path))
    }

    return null
  }

  override fun isApplicable(path: String): Boolean {
    return !isHunspellPluginInstalled() && HunspellDictionary.isHunspell(path)
  }

  override fun getDictionaryType() = SpellCheckerBundle.message("hunspell.dictionary")
}
