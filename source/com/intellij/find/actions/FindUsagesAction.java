package com.intellij.find.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.find.FindBundle;
import com.intellij.CommonBundle;

public class FindUsagesAction extends AnAction {

  public FindUsagesAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if(project==null){
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    UsageTarget[] usageTargets = (UsageTarget[])dataContext.getData(UsageView.USAGE_TARGETS);
    if (usageTargets != null) {
      usageTargets[0].findUsages();
    }
    else {
      Messages.showMessageDialog(
        project,
        FindBundle.message("find.no.usages.at.cursor.error"),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  public void update(AnActionEvent event){
    FindUsagesInFileAction.updateFindUsagesAction(event);
  }
}
