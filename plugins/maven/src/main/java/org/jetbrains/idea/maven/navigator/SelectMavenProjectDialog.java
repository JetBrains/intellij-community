package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.NullNode;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.idea.maven.project.MavenProjectModel;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class SelectMavenProjectDialog extends SelectFromMavenTreeDialog {
  private MavenProjectModel myResult;

  public SelectMavenProjectDialog(Project project, final MavenProjectModel current) {
    super(project, "Select Maven Project", MavenTreeStructure.PomNode.class, new NodeSelector() {
      public SimpleNode findNode(MavenTreeStructure.PomNode pomNode) {
        return pomNode.getProjectModel() == current ? pomNode : null;
      }
    });

    init();
  }

  @Override
  protected Action[] createActions() {
    Action selectNoneAction = new AbstractAction("&None") {
      public void actionPerformed(ActionEvent e) {
        doOKAction();
        myResult = null;
      }
    };
    return new Action[]{selectNoneAction, getOKAction(), getCancelAction()};
  }

  @Override
  protected void doOKAction() {
    SimpleNode node = getSelectedNode();
    if (node instanceof NullNode) node = null;

    if (node != null) {
      if (!(node instanceof MavenTreeStructure.PomNode)) {
        ((MavenTreeStructure.CustomNode)node).getParent(MavenTreeStructure.PomNode.class);
      }
    }
    myResult = node != null ? ((MavenTreeStructure.PomNode)node).getProjectModel() : null;

    super.doOKAction();
  }

  public MavenProjectModel getResult() {
    return myResult;
  }
}
