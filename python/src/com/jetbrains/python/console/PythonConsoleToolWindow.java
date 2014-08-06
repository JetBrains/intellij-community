package com.jetbrains.python.console;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author traff
 */
public class PythonConsoleToolWindow {

  private final Project myProject;

  private boolean myInitialized = false;

  public PythonConsoleToolWindow(Project project) {
    myProject = project;
  }

  public static PythonConsoleToolWindow getInstance(@NotNull Project project) {
    return project.getComponent(PythonConsoleToolWindow.class);
  }


  public void init(final @NotNull ToolWindow toolWindow, final @NotNull RunContentDescriptor contentDescriptor) {
    addContent(toolWindow, contentDescriptor);

    if (!myInitialized) {
      doInit(toolWindow);
    }
  }

  private void doInit(final ToolWindow toolWindow) {
    myInitialized = true;

    toolWindow.setToHideOnEmptyContent(true);

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        ToolWindow window = getToolWindow();
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible && toolWindow.getContentManager().getContentCount() == 0) {
            RunPythonConsoleAction.runPythonConsole(myProject, null, toolWindow);
          }
        }
      }
    });
  }

  private static void addContent(ToolWindow toolWindow, RunContentDescriptor contentDescriptor) {
    toolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");

    Content content = toolWindow.getContentManager().findContent(contentDescriptor.getDisplayName());
    if (content == null) {
      content = createContent(contentDescriptor);
      toolWindow.getContentManager().addContent(content);
    }
    else {
      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      resetContent(contentDescriptor, panel, content);
    }

    toolWindow.getContentManager().setSelectedContent(content);
  }

  public ToolWindow getToolWindow() {
    return ToolWindowManager.getInstance(myProject).getToolWindow(PythonConsoleToolWindowFactory.ID);
  }

  private static Content createContent(final @NotNull RunContentDescriptor contentDescriptor) {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, contentDescriptor.getDisplayName(), false);
    content.setCloseable(true);

    resetContent(contentDescriptor, panel, content);

    return content;
  }

  private static void resetContent(RunContentDescriptor contentDescriptor, SimpleToolWindowPanel panel, Content content) {
    panel.setContent(contentDescriptor.getComponent());
    //panel.addFocusListener(createFocusListener(toolWindow));

    content.setComponent(panel);
    content.setPreferredFocusableComponent(contentDescriptor.getComponent());
  }

  private static FocusListener createFocusListener(final ToolWindow toolWindow) {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus(toolWindow);
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {

      }
    };
  }

  private static JComponent getComponentToFocus(ToolWindow window) {
    return window.getContentManager().getComponent();
  }
}
