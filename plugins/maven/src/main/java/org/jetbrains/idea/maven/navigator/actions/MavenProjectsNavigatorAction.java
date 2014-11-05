/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

public abstract class MavenProjectsNavigatorAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final MavenProjectsNavigator navigator = getNavigator(e);
    return navigator!= null && isSelected(navigator);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final MavenProjectsNavigator navigator = getNavigator(e);
    if (navigator != null) {
      setSelected(navigator, state);
    }
  }

  @Nullable
  private static MavenProjectsNavigator getNavigator(AnActionEvent e) {
    final Project project = MavenActionUtil.getProject(e.getDataContext());
    return project != null ? MavenProjectsNavigator.getInstance(project) : null;
  }

  protected abstract boolean isSelected(@NotNull MavenProjectsNavigator navigator);

  protected abstract void setSelected(@NotNull MavenProjectsNavigator navigator, boolean value);
}
