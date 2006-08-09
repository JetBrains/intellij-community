/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.ide.ui.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.DocumentEvent;
import java.util.Map;
import java.util.Set;

/**
 * User: anna
 * Date: 13-Feb-2006
 */
public abstract class SearchableOptionsRegistrar{
  public static SearchableOptionsRegistrar getInstance(){
    return ApplicationManager.getApplication().getComponent(SearchableOptionsRegistrar.class);
  }

  @NotNull
  public abstract Set<Configurable> getConfigurables(final ConfigurableGroup[] groups,
                                                     final DocumentEvent.EventType type,
                                                     final Set<Configurable> configurables,
                                                     final String option,
                                                     final boolean showProjectCodeStyle);

  public abstract String getInnerPath(SearchableConfigurable configurable, String option);

  public abstract void addOption(SearchableConfigurable configurable, String option, String path, String hit);

  public abstract boolean isStopWord(String word);

  public abstract Set<String> getSynonym(final String option, @NotNull final SearchableConfigurable configurable);

  public abstract Set<String> replaceSynonyms(Set<String> options, SearchableConfigurable configurable);

  public abstract Map<String, Set<String>> findPossibleExtension(@NotNull String prefix, final Project project);


  public abstract Set<String> getProcessedWordsWithoutStemming(@NotNull String text);

  public abstract Set<String> getProcessedWords(@NotNull String text);

}
