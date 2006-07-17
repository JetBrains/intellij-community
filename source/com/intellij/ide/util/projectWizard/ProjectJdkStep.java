package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

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
  private boolean myInitialized = false;

  public ProjectJdkStep(WizardContext context) {
    myContext = context;
    myJdkChooser = new JdkChooserPanel(true);

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    final JLabel label = new JLabel(IdeBundle.message("prompt.please.select.project.jdk"));
    label.setUI(new MultiLineLabelUI());
    myPanel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));

    final JLabel jdklabel = new JLabel(IdeBundle.message("label.project.jdk"));
    jdklabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myPanel.add(jdklabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));

    myPanel.add(myJdkChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(2, 10, 10, 5), 0, 0));
    JButton configureButton = new JButton(IdeBundle.message("button.configure"));
    myPanel.add(configureButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(2, 0, 10, 5), 0, 0));

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


  public void updateStep() {
    if (!myInitialized) { //lazy default project initialization 
      ProjectJdk defaultJdk = getDefaultJdk();
      if(defaultJdk != null) {
        myJdkChooser.selectJdk(defaultJdk);
      }
      myInitialized = true;
    }
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
        IdeBundle.message("prompt.confirm.project.no.jdk"),
        IdeBundle.message("title.no.jdk.specified"),
        Messages.getWarningIcon()
      );
      if(result != 0) {
        return false;
      }
    }
    return true;
  }



}
