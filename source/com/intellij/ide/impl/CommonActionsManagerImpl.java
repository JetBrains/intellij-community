package com.intellij.ide.impl;

import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.AutoScrollToSourceHandler;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 20, 2004
 * Time: 9:42:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class CommonActionsManagerImpl extends CommonActionsManager implements ApplicationComponent {
  public AnAction createPrevOccurenceAction(OccurenceNavigator navigator) {
    return new PreviousOccurenceToolbarAction(navigator);
  }

  public AnAction createNextOccurenceAction(OccurenceNavigator navigator) {
    return new NextOccurenceToolbarAction(navigator);
  }

  public AnAction createExpandAllAction(TreeExpander expander) {
    return new ExpandAllToolbarAction(expander);
  }

  public AnAction createExpandAllAction(TreeExpander expander, JComponent component) {
    final ExpandAllToolbarAction expandAllToolbarAction = new ExpandAllToolbarAction(expander);
    expandAllToolbarAction.registerCustomShortcutSet(expandAllToolbarAction.getShortcutSet(), component);
    return expandAllToolbarAction;
  }

  public AnAction createCollapseAllAction(TreeExpander expander) {
    return new CollapseAllToolbarAction(expander);
  }

  public AnAction createCollapseAllAction(TreeExpander expander, JComponent component) {
    final CollapseAllToolbarAction collapseAllToolbarAction = new CollapseAllToolbarAction(expander);
    collapseAllToolbarAction.registerCustomShortcutSet(collapseAllToolbarAction.getShortcutSet(), component);
    return collapseAllToolbarAction;
  }

  public AnAction createHelpAction(String helpId) {
    return new HelpAction(helpId);
  }

  public AnAction installAutoscrollToSourceHandler(Project project, JTree tree, final AutoScrollToSourceOptionProvider optionProvider) {
    AutoScrollToSourceHandler handler = new AutoScrollToSourceHandler() {
      public boolean isAutoScrollMode() {
        return optionProvider.isAutoScrollMode();
      }

      public void setAutoScrollMode(boolean state) {
        optionProvider.setAutoScrollMode(state);
      }
    };
    handler.install(tree);
    return handler.createToggleAction();
  }

  public AnAction createExportToTextFileAction(ExporterToTextFile exporter) {
    return new ExportToTextFileToolbarAction(exporter);
  }

  public String getComponentName() {
    return "CommonActionsManager";
  }
  public void initComponent() {}
  public void disposeComponent() {}

  private static final class HelpAction extends AnAction {
    private String myHelpId;

    private HelpAction(String helpId) {
      super(IdeBundle.message("action.help"), null, IconLoader.getIcon("/actions/help.png"));
      myHelpId = helpId;
    }

    public void actionPerformed(AnActionEvent e) {
      HelpManager.getInstance().invokeHelp(myHelpId); //
    }
  }
}
