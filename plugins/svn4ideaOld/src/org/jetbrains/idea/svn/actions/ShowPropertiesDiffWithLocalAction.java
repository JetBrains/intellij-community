/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;

public class ShowPropertiesDiffWithLocalAction extends AbstractShowPropertiesDiffAction {
  private final Icon myIcon;

  public ShowPropertiesDiffWithLocalAction() {
    super(SvnBundle.message("action.Subversion.properties.diff.with.local.name"));
    myIcon = IconLoader.getIcon("/icons/PropertiesDiffWithLocal.png");
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);

    //e.getPresentation().setText(SvnBundle.message("action.Subversion.properties.diff.with.local.name"));
    e.getPresentation().setIcon(myIcon);
  }

  protected DataKey<Change[]> getChangesKey() {
    return VcsDataKeys.CHANGE_LEAD_SELECTION;
  }

  @Nullable
  protected SVNRevision getBeforeRevisionValue(final Change change, final SvnVcs vcs) throws SVNException {
    return SVNRevision.HEAD;
  }

  @Nullable
  protected SVNRevision getAfterRevisionValue(final Change change, final SvnVcs vcs) throws SVNException {
    return SVNRevision.WORKING;
  }
}
