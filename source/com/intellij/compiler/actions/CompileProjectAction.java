package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.ProfilingUtil;

public class CompileProjectAction extends CompileActionBase {
  protected void doAction(DataContext dataContext, final Project project) {
    ProfilingUtil.operationStarted("make");

    CompilerManager.getInstance(project).rebuild(new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings) {
        //TODO move this option to the other configuration object
        if (!aborted && LvcsConfiguration.getInstance().ADD_LABEL_ON_PROJECT_COMPILATION) {
          String text = getTemplatePresentation().getText();
          LocalVcs.getInstance(project).addLabel(errors == 0 ? "'" + text + "' with no errors" : "'" + text + "' with errors", "");
        }
      }
    });
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null);
  }
}