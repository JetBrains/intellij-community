/*
 * User: anna
 * Date: 27-Jul-2007
 */
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.content.Content;

public abstract class ChangeListTodosPanel extends TodoPanel{
  private MyChangeListManagerListener myChangeListManagerListener = new MyChangeListManagerListener();

  public ChangeListTodosPanel(Project project,TodoPanelSettings settings, Content content){
    super(project,settings,false,content);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    changeListManager.addChangeListListener(myChangeListManagerListener);
  }

  void dispose(){
    ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListManagerListener);
    super.dispose();
  }

  private final class MyChangeListManagerListener extends ChangeListAdapter {
    public void defaultListChanged(final ChangeList newDefaultList) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myTodoTreeBuilder.rebuildCache();
    }
  }
}