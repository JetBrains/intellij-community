// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.ignore.FileGroupInfo;
import org.jetbrains.idea.svn.ignore.IgnoreGroupHelperAction;
import org.jetbrains.idea.svn.ignore.IgnoreInfoGetter;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;

import java.util.Map;
import java.util.Set;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class IgnoreActionGroup extends DefaultActionGroup implements DumbAware {
  private final IgnoreGroupHelperAction myHelperAction;
  private final IgnoreInfoGetterStub myGetterStub;

  private final RemoveFromIgnoreListAction myRemoveExactAction;
  private final RemoveFromIgnoreListAction myRemoveExtensionAction;
  private final AddToIgnoreListAction myAddExactAction;
  private final AddToIgnoreListAction myAddExtensionAction;

  public IgnoreActionGroup() {
    myHelperAction = new IgnoreGroupHelperAction();
    myGetterStub = new IgnoreInfoGetterStub();

    myAddExactAction = new AddToIgnoreListAction(myGetterStub, false);
    myAddExtensionAction = new AddToIgnoreListAction(myGetterStub, true);
    myRemoveExactAction = new RemoveFromIgnoreListAction(false, myGetterStub);
    myRemoveExtensionAction = new RemoveFromIgnoreListAction(true, myGetterStub);
  }

  private static class IgnoreInfoGetterStub implements IgnoreInfoGetter {
    private IgnoreInfoGetter myGetter;

    private void setDelegate(final IgnoreInfoGetter getter) {
      myGetter = getter;
    }

    @Override
    public Map<VirtualFile, Set<String>> getInfo(final boolean useCommonExtension) {
      return myGetter.getInfo(useCommonExtension);
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final FileGroupInfo fileGroupInfo = new FileGroupInfo();

    myHelperAction.setFileIterationListener(fileGroupInfo);
    myHelperAction.update(e);

    myGetterStub.setDelegate(fileGroupInfo);

    if ((e.getPresentation().isEnabled())) {
      removeAll();
      if (myHelperAction.allAreIgnored()) {
        final DataContext dataContext = e.getDataContext();
        final Project project = CommonDataKeys.PROJECT.getData(dataContext);
        SvnVcs vcs = SvnVcs.getInstance(project);

        final Ref<Boolean> filesOk = new Ref<>(Boolean.FALSE);
        final Ref<Boolean> extensionOk = new Ref<>(Boolean.FALSE);

        // virtual files parameter is not used -> can pass null
        SvnPropertyService.doCheckIgnoreProperty(vcs, null, fileGroupInfo, fileGroupInfo.getExtensionMask(), filesOk, extensionOk);

        if (Boolean.TRUE.equals(filesOk.get())) {
          myRemoveExactAction
            .setActionText(fileGroupInfo.oneFileSelected() ? fileGroupInfo.getFileName() : message("action.Subversion.UndoIgnore.text"));
          add(myRemoveExactAction);
        }

        if (Boolean.TRUE.equals(extensionOk.get())) {
          myRemoveExtensionAction.setActionText(fileGroupInfo.getExtensionMask());
          add(myRemoveExtensionAction);
        }

        e.getPresentation().setText(messagePointer("group.RevertIgnoreChoicesGroup.text"));
      } else if (myHelperAction.allCanBeIgnored()) {
        myAddExactAction.setActionText(
          fileGroupInfo.oneFileSelected() ? fileGroupInfo.getFileName() : message("action.Subversion.Ignore.ExactMatch.text"));
        add(myAddExactAction);
        if (fileGroupInfo.sameExtension()) {
          myAddExtensionAction.setActionText(fileGroupInfo.getExtensionMask());
          add(myAddExtensionAction);
        }
        e.getPresentation().setText(messagePointer("group.IgnoreChoicesGroup.text"));
      }
    }
  }
}
