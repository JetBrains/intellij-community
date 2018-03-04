package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class RunAnythingSEOption extends BooleanOptionDescription {
  @NotNull private final String myKey;
  @NotNull private final Project myProject;

  public RunAnythingSEOption(@NotNull Project project, @NotNull String option, @NotNull String registryKey) {
    super(option, null);
    myProject = project;
    myKey = registryKey;
  }

  @Override
  public boolean isOptionEnabled() {
    return RunAnythingCache.getInstance(myProject).isGroupVisible(myKey);
  }

  @Override
  public void setOptionState(boolean enabled) {
    RunAnythingCache.getInstance(myProject).saveGroupVisibilityKey(myKey, enabled);
  }
}
