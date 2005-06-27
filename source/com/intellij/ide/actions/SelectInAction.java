package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ListPopup;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class SelectInAction extends AnAction {
  
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.select.in");
    SelectInContextImpl.SelectInContextProvider context = SelectInContextImpl.createContext(e);
    if (context == null) return;
    invoke(e.getDataContext(), context);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (SelectInContextImpl.createContext(event) == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    } else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  private static void invoke(DataContext dataContext, SelectInContextImpl.SelectInContextProvider contextProvider) {
    final SelectInContext context = contextProvider.getContext();
    final SelectInTarget[] targetVector = getTargets(context.getProject());

    DefaultActionGroup group = new DefaultActionGroup();
    boolean hasEnabled = false;
    for (SelectInTarget target : targetVector) {
      final boolean enabled = target.canSelect(context);
      hasEnabled |= enabled;
      group.add(new TargetSelectedAction(target, context, enabled));
    }

    if (!hasEnabled) {
      group.removeAll();
      group.add(new NoTargetsAction());
    }

    ListPopup listPopup = ActionListPopup.createListPopup("Select Target", group, dataContext, true, true);

    Point p = contextProvider.getInvocationPoint();

    listPopup.show(p.x, p.y);
  }

  private static SelectInTarget[] getTargets(final Project project) {
    ArrayList<SelectInTarget> result = new ArrayList<SelectInTarget>(Arrays.asList(getTargetsFor(project)));

    if (result.size() > 1) {
      rearrangeTargetList(project, result);
    }

    return result.toArray(new SelectInTarget[result.size()]);
  }

  private static SelectInTarget[] getTargetsFor(final Project project) {
    return getSelectInManager(project).getTargets();
  }

  private static SelectInManager getSelectInManager(Project project) {
    return SelectInManager.getInstance(project);
  }

  private static void rearrangeTargetList(final Project project, final ArrayList<SelectInTarget> result) {
    final String activeToolWindowId = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (activeToolWindowId != null) {
      SelectInTarget firstTarget = result.get(0);
      if (activeToolWindowId.equals(firstTarget.getToolWindowId())) {
        boolean shouldMoveToBottom = true;
        if (ToolWindowId.PROJECT_VIEW.equals(activeToolWindowId)) {
          final String currentMinorViewId = ProjectView.getInstance(project).getCurrentViewId();
          shouldMoveToBottom = currentMinorViewId != null && currentMinorViewId.equals(firstTarget.getMinorViewId());
        }
        if (shouldMoveToBottom) {
          result.remove(0);
          result.add(firstTarget);
        }
      }
    }
  }


  private static class TargetSelectedAction extends AnAction {
    private SelectInTarget myTarget;
    private SelectInContext myContext;

    public TargetSelectedAction(final SelectInTarget target, final SelectInContext context, final boolean enabled) {
      super(target.toString());
      getTemplatePresentation().setEnabled(enabled);
      myTarget = target;
      myContext = context;
    }

    public void actionPerformed(AnActionEvent e) {
      myTarget.selectIn(myContext, true);
    }
  }

  private static class NoTargetsAction extends AnAction {
    public NoTargetsAction() {
      super("No targets available in this context");
    }

    public void actionPerformed(AnActionEvent e) {
    }
  }
}