package com.intellij.debugger.actions;

import com.intellij.debugger.settings.*;
import com.intellij.debugger.ui.PropertiesDialog;
import com.intellij.debugger.ui.impl.FrameDebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.Configurable;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 4:39:53 PM
 */
public class CustomizeContextViewAction extends DebuggerAction{
  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);

    CompositeConfigurable configurable = new CompositeConfigurable() {
      protected List<Configurable> createConfigurables() {
        ArrayList<Configurable> array = new ArrayList<Configurable>();
        array.add(new ViewsGeneralConfigurable());
        array.add(new NodeRendererConfigurable(project));
        return array;
      }

      public String getDisplayName() {
        return "Customize view";
      }

      public Icon getIcon() {
        return null;
      }

      public String getHelpTopic() {
        return null;
      }
    };

    PropertiesDialog dialog = new PropertiesDialog(configurable, project);
    dialog.show();
  }

  public void update(AnActionEvent e) {
    DebuggerTree tree = getTree(e.getDataContext());
    e.getPresentation().setVisible(tree instanceof FrameDebuggerTree);
    e.getPresentation().setText("Customize View...");
  }
}
