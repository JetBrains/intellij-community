package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 21, 2004
 */
public class ProjectJdkStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private JdkChooserPanel myJdkChooser;
  private JPanel myPanel;
  private WizardContext myContext;

  public ProjectJdkStep(WizardContext context) {
    myContext = context;
    myJdkChooser = new JdkChooserPanel();

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    final JLabel label = new JLabel("Please select project JDK.\nThis JDK will be used by default by all project modules.");
    label.setUI(new MultiLineLabelUI());
    myPanel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));

    final JLabel jdklabel = new JLabel("Project JDK: ");
    jdklabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
    myPanel.add(jdklabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));

    myPanel.add(myJdkChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(2, 10, 10, 5), 0, 0));
    JButton configureButton = new JButton("Configure...");
    configureButton.setMnemonic('C');
    myPanel.add(configureButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(2, 0, 10, 5), 0, 0));

    ProjectJdk defaultJdk = getDefaultJdk();
    if(defaultJdk != null) {
      myJdkChooser.selectJdk(defaultJdk);
    }

    configureButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myJdkChooser.editJdkTable();
      }
    });
  }

  public JComponent getPreferredFocusedComponent() {
    return myJdkChooser.getPreferredFocusedComponent();
  }

  public String getHelpId() {
    return "project.new.page2";
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myContext.setProjectJdk(getJdk());
  }

  public ProjectJdk getJdk() {
    return myJdkChooser.getChosenJdk();
  }

  public Icon getIcon() {
    return NEW_PROJECT_ICON;
  }

  private static ProjectJdk getDefaultJdk() {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    return ProjectRootManagerEx.getInstanceEx(defaultProject).getProjectJdk();
  }


  public boolean validate() {
    final ProjectJdk jdk = myJdkChooser.getChosenJdk();
    if (jdk == null) {
      int result = Messages.showOkCancelDialog(
        "Do you want to create a project with no JDK assigned?\n"+
        "JDK is required for compiling, debugging and running applications\n"+
        "as well as for standard JDK classes resolution.",
        "No JDK Specified",
        Messages.getWarningIcon()
      );
      if(result != 0) {
        return false;
      }
    }
    return true;
  }



}
