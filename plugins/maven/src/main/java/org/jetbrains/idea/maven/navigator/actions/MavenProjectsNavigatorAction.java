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
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

public abstract class MavenProjectsNavigatorAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return isSelected(getNavigator(e));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    setSelected(getNavigator(e), state);
  }

  private MavenProjectsNavigator getNavigator(AnActionEvent e) {
    return MavenProjectsNavigator.getInstance(MavenActionUtil.getProject(e.getDataContext()));
  }

  protected abstract boolean isSelected(MavenProjectsNavigator navigator);

  protected abstract void setSelected(MavenProjectsNavigator navigator, boolean value);
}
