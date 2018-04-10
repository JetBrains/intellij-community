// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesOptions;
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
  public void loadState(@NotNull State state) {
    isSearchForTextOccurrences = state.isSearchForTextOccurrences;
  }

  static class State {
    public boolean isSearchForTextOccurrences;
  }
}
