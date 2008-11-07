package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.NullNode;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.idea.maven.project.MavenProjectModel;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class SelectMavenProjectDialog extends SelectFromMavenProjectsDialog {
  private MavenProjectModel myResult;

  public SelectMavenProjectDialog(Project project, final MavenProjectModel current) {
    super(project, "Select Maven Project", MavenProjectsStructure.PomNode.class, new NodeSelector() {
      public SimpleNode findNode(MavenProjectsStructure.PomNode pomNode) {
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
      if (!(node instanceof MavenProjectsStructure.PomNode)) {
        ((MavenProjectsStructure.CustomNode)node).getParent(MavenProjectsStructure.PomNode.class);
      }
    }
    myResult = node != null ? ((MavenProjectsStructure.PomNode)node).getProjectModel() : null;

    super.doOKAction();
  }

  public MavenProjectModel getResult() {
    return myResult;
  }
}
