// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;

import javax.swing.*;
import java.util.LinkedHashMap;

public class AutoSvnUpdater extends AbstractCommonUpdateAction {
  private final Project myProject;
  private final FilePath[] myRoots;

  public AutoSvnUpdater(final Project project, final FilePath[] roots) {
    super(BlindUpdateAction.ourInstance, new AutoUpdateScope(roots), false);
    myProject = project;
    myRoots = roots;
  }

  public static void run(@NotNull AutoSvnUpdater updater, @NotNull String title) {
    JComponent frame = WindowManager.getInstance().getIdeFrame(updater.myProject).getComponent();

    updater.getTemplatePresentation().setText(title);
    updater.actionPerformed(
      AnActionEvent.createFromAnAction(updater, null, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(frame))
    );
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    final SvnConfiguration configuration17 = SvnConfiguration.getInstance(myProject);
    configuration17.setForceUpdate(false);
    configuration17.setUpdateDepth(Depth.INFINITY);
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    for (FilePath root : myRoots) {
      configureUpdateRootInfo(root, configuration17.getUpdateRootInfo(root.getIOFile(), vcs));
    }
    super.actionPerformed(context);
  }

  protected void configureUpdateRootInfo(@NotNull FilePath root, @NotNull UpdateRootInfo info) {
    info.setRevision(Revision.HEAD);
    info.setUpdateToRevision(false);
  }

  @Override
  protected boolean filterRootsBeforeAction() {
    return false;
  }

  private static class BlindUpdateAction implements ActionInfo {
    private final static BlindUpdateAction ourInstance = new BlindUpdateAction();

    @Override
    public boolean showOptions(Project project) {
      return false;
    }

    @Override
    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getUpdateEnvironment();
    }

    @Override
    public UpdateOrStatusOptionsDialog createOptionsDialog(Project project,
                                                           LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                           String scopeName) {
      // should not be called
      return null;
    }

    @Override
    public String getActionName(String scopeName) {
      return ActionInfo.UPDATE.getActionName(scopeName);
    }

    @Override
    public String getActionName() {
      return ActionInfo.UPDATE.getActionName();
    }

    @Override
    public String getGroupName(FileGroup fileGroup) {
      return ActionInfo.UPDATE.getGroupName(fileGroup);
    }

    @Override
    public boolean canGroupByChangelist() {
      return ActionInfo.UPDATE.canGroupByChangelist();
    }

    @Override
    public boolean canChangeFileStatus() {
      return ActionInfo.UPDATE.canChangeFileStatus();
    }
  }

  private static final class AutoUpdateScope implements ScopeInfo {
    private final FilePath[] myRoots;

    private AutoUpdateScope(final FilePath[] roots) {
      myRoots = roots;
    }

    @Override
    public FilePath[] getRoots(VcsContext context, ActionInfo actionInfo) {
      return myRoots;
    }

    @Override
    public String getScopeName(VcsContext dataContext, ActionInfo actionInfo) {
      return "Subversion";
    }

    @Override
    public boolean filterExistsInVcs() {
      return false;
    }
  }
}
