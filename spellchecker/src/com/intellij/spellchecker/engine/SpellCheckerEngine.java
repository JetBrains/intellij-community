/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.engine;

import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.Loader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SpellCheckerEngine {
  void loadDictionary(@NotNull Loader loader);

  void addModifiableDictionary(@NotNull EditableDictionary dictionary);

  void addDictionary(@NotNull Dictionary dictionary);

  Transformation getTransformation();

  boolean isCorrect(@NotNull String word);


  @NotNull
  List<String> getSuggestions(@NotNull String word, int threshold, int quality);

  @NotNull
  List<String> getVariants(@NotNull String prefix);


  void reset();

  boolean isDictionaryLoad(@NotNull String name);

  void removeDictionary(@NotNull String name);

  void removeDictionariesRecursively(@NotNull String directory);
}
