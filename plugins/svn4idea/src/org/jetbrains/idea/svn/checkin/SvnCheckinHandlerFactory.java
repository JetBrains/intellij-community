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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.AutoSvnUpdater;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
      @Override
      public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        return null;
      }

      @Override
      public void checkinSuccessful() {
        if (SvnConfiguration.getInstance(project).isAutoUpdateAfterCommit()) {
          final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(SvnVcs.getInstance(project));
          final List<FilePath> paths = new ArrayList<FilePath>();
          for (int i = 0; i < roots.length; i++) {
            VirtualFile root = roots[i];
            boolean take = false;
            for (VirtualFile commitRoot : commitRoots) {
              if (VfsUtil.isAncestor(root, commitRoot, false)) {
                take = true;
                break;
              }
            }
            if (! take) continue;
            paths.add(new FilePathImpl(root));
          }
          if (paths.isEmpty()) return;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              final JComponent frame = WindowManager.getInstance().getIdeFrame(project).getComponent();
              final AutoSvnUpdater updater = new AutoSvnUpdater(project, paths.toArray(new FilePath[paths.size()]));
              updater.getTemplatePresentation().setText(ActionInfo.UPDATE.getActionName());
              updater.actionPerformed(
                new AnActionEvent(null, DataManager.getInstance().getDataContext(frame), ActionPlaces.UNKNOWN,
                                  updater.getTemplatePresentation(), ActionManager.getInstance(), 0));
            }
          }, ModalityState.NON_MODAL);
        }
      }
    };
  }
}
