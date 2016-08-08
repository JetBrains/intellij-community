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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.ignore.FileGroupInfo;
import org.jetbrains.idea.svn.ignore.IgnoreGroupHelperAction;
import org.jetbrains.idea.svn.ignore.IgnoreInfoGetter;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;

import java.util.Map;
import java.util.Set;

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

    public Map<VirtualFile, Set<String>> getInfo(final boolean useCommonExtension) {
      return myGetter.getInfo(useCommonExtension);
    }
  }

  public void update(final AnActionEvent e) {
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
        SvnPropertyService.doCheckIgnoreProperty(vcs, project, null, fileGroupInfo, fileGroupInfo.getExtensionMask(), filesOk, extensionOk);

        if (Boolean.TRUE.equals(filesOk.get())) {
          myRemoveExactAction.setActionText(fileGroupInfo.oneFileSelected() ?
            fileGroupInfo.getFileName() : SvnBundle.message("action.Subversion.UndoIgnore.text"));
          add(myRemoveExactAction);
        }

        if (Boolean.TRUE.equals(extensionOk.get())) {
          myRemoveExtensionAction.setActionText(fileGroupInfo.getExtensionMask());
          add(myRemoveExtensionAction);
        }

        e.getPresentation().setText(SvnBundle.message("group.RevertIgnoreChoicesGroup.text"));
      } else if (myHelperAction.allCanBeIgnored()) {
        final String ignoreExactlyName = (fileGroupInfo.oneFileSelected()) ?
                                         fileGroupInfo.getFileName() : SvnBundle.message("action.Subversion.Ignore.ExactMatch.text");
        myAddExactAction.setActionText(ignoreExactlyName);
        add(myAddExactAction);
        if (fileGroupInfo.sameExtension()) {
          myAddExtensionAction.setActionText(fileGroupInfo.getExtensionMask());
          add(myAddExtensionAction);
        }
        e.getPresentation().setText(SvnBundle.message("group.IgnoreChoicesGroup.text"));
      }
    }
  }
}
