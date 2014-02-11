/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.vcs.changes.committed.ChangeListFilteringStrategy;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import icons.SvnIcons;
import org.jetbrains.idea.svn.SvnBundle;

/**
* @author Konstantin Kolosovsky.
*/
public class ShowHideMergePanelAction extends ToggleAction {

  private final DecoratorManager myManager;
  private final ChangeListFilteringStrategy myStrategy;
  private boolean myIsSelected;

  public ShowHideMergePanelAction(final DecoratorManager manager, final ChangeListFilteringStrategy strategy) {
    myManager = manager;
    myStrategy = strategy;
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setIcon(SvnIcons.ShowIntegratedFrom);
    presentation.setText(SvnBundle.message("committed.changes.action.enable.merge.highlighting"));
    presentation.setDescription(SvnBundle.message("committed.changes.action.enable.merge.highlighting.description.text"));
  }

  public boolean isSelected(final AnActionEvent e) {
    return myIsSelected;
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    myIsSelected = state;
    if (state) {
      myManager.setFilteringStrategy(myStrategy);
    } else {
      myManager.removeFilteringStrategy(myStrategy.getKey());
    }
  }
}
