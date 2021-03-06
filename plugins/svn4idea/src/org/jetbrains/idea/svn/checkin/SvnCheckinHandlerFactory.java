// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.update.AutoSvnUpdater;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.ui.Messages.showOkCancelDialog;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnCheckinHandlerFactory extends VcsCheckinHandlerFactory {
  public SvnCheckinHandlerFactory() {
    super(SvnVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    final Project project = panel.getProject();
    return new CheckinHandler() {
      @Override
      public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        return null;
      }

      @Override
      public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
        if (executor instanceof LocalCommitExecutor) return ReturnResult.COMMIT;
        final SvnVcs vcs = SvnVcs.getInstance(project);
        MultiMap<Url, WorkingCopyFormat> copiesInfo = splitIntoCopies(vcs, panel.getSelectedChanges());
        List<Url> repoUrls = new ArrayList<>();
        for (Map.Entry<Url, Collection<WorkingCopyFormat>> entry : copiesInfo.entrySet()) {
          if (entry.getValue().size() > 1) {
            repoUrls.add(entry.getKey());
          }
        }
        if (! repoUrls.isEmpty()) {
          String join = StringUtil.join(repoUrls, Url::toDecodedString, ",\n");
          final int isOk = showOkCancelDialog(
            project,
            message("checkin.different.formats.involved", repoUrls.size() > 1 ? 1 : 0, join),
            message("dialog.title.commit.will.split"),
            Messages.getWarningIcon()
          );

          return Messages.OK == isOk ? ReturnResult.COMMIT : ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }

      @Override
      public void checkinSuccessful() {
        if (SvnConfiguration.getInstance(project).isAutoUpdateAfterCommit()) {
          final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(SvnVcs.getInstance(project));
          final List<FilePath> paths = new ArrayList<>();
          for (VirtualFile root : roots) {
            boolean take = false;
            for (VirtualFile commitRoot : panel.getRoots()) {
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
          ApplicationManager.getApplication().invokeLater(
            () -> AutoSvnUpdater
              .run(new AutoSvnUpdater(project, paths.toArray(new FilePath[0])), VcsBundle.message("action.name.update")),
            ModalityState.NON_MODAL);
        }
      }
    };
  }

  @NotNull
  private static MultiMap<Url, WorkingCopyFormat> splitIntoCopies(@NotNull SvnVcs vcs, @NotNull Collection<Change> changes) {
    MultiMap<Url, WorkingCopyFormat> result = MultiMap.createSet();
    SvnFileUrlMapping mapping = vcs.getSvnFileUrlMapping();

    for (Change change : changes) {
      RootUrlInfo path = mapping.getWcRootForFilePath(ChangesUtil.getFilePath(change));

      if (path != null) {
        result.putValue(path.getRepositoryUrl(), path.getFormat());
      }
    }

    return result;
  }
}
