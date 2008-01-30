package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.XDebuggerSupport;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerActionBase extends AnAction {
  private boolean myHideDisabledInPopup;

  protected XDebuggerActionBase() {
    this(false);
  }

  protected XDebuggerActionBase(final boolean hideDisabledInPopup) {
    myHideDisabledInPopup = hideDisabledInPopup;
  }

  public void update(final AnActionEvent event) {
    boolean enabled = isEnabled(event);
    Presentation presentation = event.getPresentation();
    if (myHideDisabledInPopup && ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(enabled);
    }
  }

  private boolean isEnabled(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      DebuggerSupport[] debuggerSupports = XDebuggerSupport.getDebuggerSupports();
      for (DebuggerSupport support : debuggerSupports) {
        if (isEnabled(project, e, support)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  protected abstract DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport);

  private boolean isEnabled(final Project project, final AnActionEvent event, final DebuggerSupport support) {
    return getHandler(support).isEnabled(project, event);
  }

  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    DebuggerSupport[] debuggerSupports = XDebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : debuggerSupports) {
      if (isEnabled(project, e, support)) {
        perform(project, e, support);
        break;
      }
    }
  }

  private void perform(final Project project, final AnActionEvent e, final DebuggerSupport support) {
    getHandler(support).perform(project, e);
  }
}
