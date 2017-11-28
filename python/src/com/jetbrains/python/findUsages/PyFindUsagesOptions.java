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
package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yuli Fiterman
 */
@State(name = "PyFindUsagesOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class PyFindUsagesOptions extends FindUsagesOptions implements PersistentStateComponent<PyFindUsagesOptions.State> {


  public PyFindUsagesOptions(@NotNull Project project) {
    super(project);
    isUsages = true;
    isSearchForTextOccurrences = false;
  }

  public static PyFindUsagesOptions getInstance(Project project) {
    return ServiceManager.getService(project, PyFindUsagesOptions.class);
  }


  @Nullable
  @Override
  public State getState() {
    State s = new State();
    s.isSearchForTextOccurrences = isSearchForTextOccurrences;
    return s;
  }

  @Override
  public void loadState(State state) {
    isSearchForTextOccurrences = state.isSearchForTextOccurrences;
  }

  static class State {
    public boolean isSearchForTextOccurrences;
  }
}
