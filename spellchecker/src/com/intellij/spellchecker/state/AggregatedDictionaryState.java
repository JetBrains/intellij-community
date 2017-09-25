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
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.dictionary.AggregatedDictionary;
import com.intellij.spellchecker.dictionary.ProjectDictionary;
import com.intellij.spellchecker.dictionary.UserDictionary;
import org.jetbrains.annotations.NotNull;

public class AggregatedDictionaryState {
  private AggregatedDictionary dictionary;
  private final ProjectDictionaryState myProjectDictionaryState;

  public AggregatedDictionaryState(@NotNull Project project) {
    CachedDictionaryState cachedDictionaryState = ServiceManager.getService(CachedDictionaryState.class);
    myProjectDictionaryState = ServiceManager.getService(project, ProjectDictionaryState.class);
    String currentUser = System.getProperty("user.name");

    ProjectDictionary projectDictionary = myProjectDictionaryState.getProjectDictionary();
    projectDictionary.setActiveName(currentUser);

    if (cachedDictionaryState.getDictionary() == null) {
      cachedDictionaryState.setDictionary(new UserDictionary(CachedDictionaryState.DEFAULT_NAME));
    }
    dictionary = new AggregatedDictionary(projectDictionary, cachedDictionaryState.getDictionary());
    cachedDictionaryState.setDictionary(dictionary.getCachedDictionary());
    myProjectDictionaryState.setProjectDictionary(dictionary.getProjectDictionary());
    myProjectDictionaryState.addProjectDictListener((dict) -> getDictionary().replaceAll(dict.getWords()));
  }

  @NotNull
  public AggregatedDictionary getDictionary() {
    return dictionary;
  }

  @Override
  public String toString() {
    return "AggregatedDictionaryState{" + "dictionary=" + dictionary + '}';
  }

  public void addDictStateListener(DictionaryStateListener listener) {
    myProjectDictionaryState.addProjectDictListener(listener);
  }
}
