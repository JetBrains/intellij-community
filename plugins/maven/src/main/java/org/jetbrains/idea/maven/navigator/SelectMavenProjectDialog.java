package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.NullNode;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.idea.maven.project.MavenProject;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class SelectMavenProjectDialog extends SelectFromMavenProjectsDialog {
  private MavenProject myResult;

  public SelectMavenProjectDialog(Project project, final MavenProject current) {
    super(project, "Select Maven Project", MavenProjectsStructure.ProjectNode.class, new NodeSelector() {
      public boolean shouldSelect(SimpleNode node) {
        if (node instanceof MavenProjectsStructure.ProjectNode) {
          return ((MavenProjectsStructure.ProjectNode)node).getMavenProject() == current;
        }
        return false;
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
      if (!(node instanceof MavenProjectsStructure.ProjectNode)) {
        ((MavenProjectsStructure.CustomNode)node).getParent(MavenProjectsStructure.ProjectNode.class);
      }
    }
    myResult = node != null ? ((MavenProjectsStructure.ProjectNode)node).getMavenProject() : null;

    super.doOKAction();
  }

  public MavenProject getResult() {
    return myResult;
  }
}
