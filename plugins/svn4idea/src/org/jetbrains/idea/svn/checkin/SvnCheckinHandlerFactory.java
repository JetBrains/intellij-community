/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.LocalCommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.update.AutoSvnUpdater;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/16/12
 * Time: 6:51 PM
 */
public class SvnCheckinHandlerFactory extends VcsCheckinHandlerFactory {
  public SvnCheckinHandlerFactory() {
    super(SvnVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(final CheckinProjectPanel panel) {
    final Project project = panel.getProject();
    final Collection<VirtualFile> commitRoots = panel.getRoots();
    return new CheckinHandler() {
      private Collection<Change> myChanges = panel.getSelectedChanges();

      @Override
      public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        return null;
      }

      @Override
      public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
        if (executor instanceof LocalCommitExecutor) return ReturnResult.COMMIT;
        final SvnVcs vcs = SvnVcs.getInstance(project);
        final MultiMap<String, WorkingCopyFormat> copiesInfo = splitIntoCopies(vcs, myChanges);
        final List<String> repoUrls = new ArrayList<>();
        for (Map.Entry<String, Collection<WorkingCopyFormat>> entry : copiesInfo.entrySet()) {
          if (entry.getValue().size() > 1) {
            repoUrls.add(entry.getKey());
          }
        }
        if (! repoUrls.isEmpty()) {
          final String join = StringUtil.join(repoUrls, ",\n");
          final int isOk = Messages.showOkCancelDialog(project,
            SvnBundle.message("checkin.different.formats.involved", repoUrls.size() > 1 ? 1 : 0, join),
            "Subversion: Commit Will Split", Messages.getWarningIcon());

          return Messages.OK == isOk ? ReturnResult.COMMIT : ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }

      @Override
      public void includedChangesChanged() {
        myChanges = panel.getSelectedChanges();
      }

      @Override
      public void checkinSuccessful() {
        if (SvnConfiguration.getInstance(project).isAutoUpdateAfterCommit()) {
          final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(SvnVcs.getInstance(project));
          final List<FilePath> paths = new ArrayList<>();
          for (VirtualFile root : roots) {
            boolean take = false;
            for (VirtualFile commitRoot : commitRoots) {
              if (VfsUtilCore.isAncestor(root, commitRoot, false)) {
                take = true;
                break;
              }
            }
            if (take) {
              paths.add(VcsUtil.getFilePath(root));
            }
          }
          if (paths.isEmpty()) return;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              AutoSvnUpdater.run(new AutoSvnUpdater(project, paths.toArray(new FilePath[paths.size()])), ActionInfo.UPDATE.getActionName());
            }
          }, ModalityState.NON_MODAL);
        }
      }
    };
  }

  @NotNull
  private static MultiMap<String, WorkingCopyFormat> splitIntoCopies(@NotNull SvnVcs vcs, @NotNull Collection<Change> changes) {
    MultiMap<String, WorkingCopyFormat> result = MultiMap.createSet();
    SvnFileUrlMapping mapping = vcs.getSvnFileUrlMapping();

    for (Change change : changes) {
      RootUrlInfo path = mapping.getWcRootForFilePath(ChangesUtil.getFilePath(change).getIOFile());

      if (path != null) {
        result.putValue(path.getRepositoryUrl(), path.getFormat());
      }
    }

    return result;
  }
}
