package com.jetbrains.python.packaging.setupPy;

import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.ListChooseByNameModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.packaging.PyPackageUtil;

/**
 * @author yole
 */
public class SetupTaskChooserAction extends AnAction {
  public SetupTaskChooserAction() {
    super("Run setup.py Task...");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) return;
    final Project project = module.getProject();
    final ListChooseByNameModel<SetupTask> model = new ListChooseByNameModel<SetupTask>(project, "Enter setup.py task name",
                                                                                        "No tasks found",
                                                                                        SetupTaskIntrospector.getTaskList(module));
    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, GotoActionBase.getPsiContext(e));
    popup.setShowListForEmptyPattern(true);

    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
      }

      public void elementChosen(Object element) {
        if (element != null) {
          final SetupTask task = (SetupTask) element;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              RunSetupTaskAction.runSetupTask(task.getName(), module);
            }
          }, ModalityState.NON_MODAL);
        }
      }
    }, ModalityState.current(), false);

  }

  @Override
  public void update(AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    e.getPresentation().setEnabled(module != null && PyPackageUtil.findSetupPy(module) != null);
  }
}
