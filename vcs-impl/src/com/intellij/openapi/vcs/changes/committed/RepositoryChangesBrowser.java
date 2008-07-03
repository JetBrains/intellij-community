package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class RepositoryChangesBrowser extends ChangesBrowser implements DataProvider {
  private CommittedChangesBrowserUseCase myUseCase;

  public RepositoryChangesBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(project, changeLists, Collections.<Change>emptyList(), null, false, false);
  }

  public RepositoryChangesBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                  final ChangeList initialListSelection) {
    super(project, changeLists, changes, initialListSelection, false, false);
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    toolBarGroup.add(new ShowDiffWithLocalAction());
    final Icon editSourceIcon = IconLoader.getIcon("/actions/editSource.png");
    toolBarGroup.add(new EditSourceAction() {
      public void update(final AnActionEvent event) {
        super.update(event);
        event.getPresentation().setIcon(editSourceIcon);
        event.getPresentation().setText("Edit Source");
        if (CommittedChangesBrowserUseCase.IN_AIR.equals(event.getDataContext().getData(CommittedChangesBrowserUseCase.CONTEXT_NAME))) {
          event.getPresentation().setEnabled(false);
        }
      }
      protected Navigatable[] getNavigatables(final DataContext dataContext) {
        Change[] changes = (Change[])dataContext.getData(VcsDataKeys.SELECTED_CHANGES.getName());
        if (changes != null) {
          Collection<Change> changeCollection = Arrays.asList(changes);
          return ChangesUtil.getNavigatableArray(myProject, ChangesUtil.getFilesFromChanges(changeCollection));
        }
        return null;
      }
    });
    OpenRepositoryVersionAction action = new OpenRepositoryVersionAction();
    toolBarGroup.add(action);

    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("RepositoryChangesBrowserToolbar");
    final AnAction[] actions = group.getChildren(null);
    for (AnAction anAction : actions) {
      toolBarGroup.add(anAction);
    }
  }

  public void setUseCase(final CommittedChangesBrowserUseCase useCase) {
    myUseCase = useCase;
  }

  public Object getData(@NonNls final String dataId) {
    if (CommittedChangesBrowserUseCase.CONTEXT_NAME.equals(dataId)) {
      return myUseCase;
    }

    else if (VcsDataKeys.SELECTED_CHANGES.getName().equals(dataId)) {
      final List<Change> list = myViewer.getSelectedChanges();
      return list.toArray(new Change [list.size()]);
    }
    return null;
  }
}