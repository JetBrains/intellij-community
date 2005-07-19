/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 08.07.2005
 * Time: 21:44:21
 * To change this template use File | Settings | File Templates.
 */
public class BrowseRepositoryAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    RepositoryBrowserDialog dialog = new RepositoryBrowserDialog(project);
    dialog.show();
  }

  public void update(AnActionEvent e) {
    super.update(e);

    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    SvnVcs vcs = project != null ? SvnVcs.getInstance(project) : null;
    e.getPresentation().setEnabled(vcs != null);
  }
}
