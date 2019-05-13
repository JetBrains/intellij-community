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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.ChangesBrowserDialog;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesTableModel;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;

public class SvnSelectRevisionUtil {
  private SvnSelectRevisionUtil() {
  }

  @Nullable
  public static SvnChangeList chooseCommittedChangeList(final Project project, final SvnRepositoryLocation location,
                                                              final VirtualFile root) {
    try {
      final SvnCommittedChangesTableModel model = new SvnCommittedChangesTableModel(location, project, root,
                                                                                    SvnVcs.getInstance(project)
                                                                                      .getCommittedChangesProvider().getColumns());
      final ChangesBrowserDialog dlg = new ChangesBrowserDialog(project, model, ChangesBrowserDialog.Mode.Choose, null);
      if (dlg.showAndGet()) {
        return (SvnChangeList)dlg.getSelectedChangeList();
      }
      model.onBeforeClose();
    }
    catch (VcsException e) {
      Messages.showErrorDialog(e.getMessage(), SvnBundle.message("error.cannot.load.revisions"));
    }
    return null;
  }
}
