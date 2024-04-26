// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.util.Pair;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author shkate@jetbrains.com
 */
public class ProjectDictionarySplitter extends StateSplitterEx {
  @Override
  public @NotNull List<Pair<Element, String>> splitState(@NotNull Element state) {
    return splitState(state, DictionaryState.NAME_ATTRIBUTE);
  }
}