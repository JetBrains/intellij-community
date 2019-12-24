// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.spellchecker.compress.CompressedDictionary;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.engine.Transformation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class SpellcheckerCorrectionsFilter {
  private static final Logger LOG = Logger.getInstance(SpellcheckerCorrectionsFilter.class);
  private static final String DICT = "excluded.dic";
  private Dictionary excludedCorrections;

  public static SpellcheckerCorrectionsFilter getInstance() {
    return ServiceManager.getService(SpellcheckerCorrectionsFilter.class);
  }

  public SpellcheckerCorrectionsFilter() {
    try (InputStream stream = getClass().getResourceAsStream(DICT)) {
      excludedCorrections = CompressedDictionary.create(new StreamLoader(stream, DICT), new Transformation());
    }
    catch (IOException e) {
      LOG.warn("Couldn't load a dictionary for exclusion");
    }
  }

  public boolean isFiltered(@NotNull String string) {
    return excludedCorrections != null && excludedCorrections.contains(string) == Boolean.TRUE;
  }
}
