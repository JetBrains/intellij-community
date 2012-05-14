package com.opsx.bean;

import com.opsx.exception.EnvironmentsNotDefinedException;
import com.opsx.main.Environment;
import com.opsx.main.EnvironmentManager;
import com.opsx.main.UserManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

/**
 * @author abrown
 *         Copyright OpsTechnology Mar 31, 2004
 */


public class Setup {


  private JTextField environmentName;
  private JTextField firstLogFolder;
  private JTextField optionalLogFolder;

  private JComboBox environmentList;
  private JButton   save;


  public JPanel getMainPanel() {
    return mainPanel;
  }


  private JPanel mainPanel;

  private EnvironmentManager environmentManager = null;
  private JTextField pathToOutputFile;


  public Setup() throws EnvironmentsNotDefinedException, IOException, ClassNotFoundException {
    UserManager userManager = UserManager.getUserManager();

    environmentManager = EnvironmentManager.getEnvironmentManager(userManager.getUserName());


    Environment currentEnvironment = environmentManager.getCurrentEnvironment();

    environmentName.setText(currentEnvironment.getName());
    firstLogFolder.setText(currentEnvironment.getFirstLogFolder());
    optionalLogFolder.setText(currentEnvironment.getOptionalLogFolder());
    pathToOutputFile.setText(currentEnvironment.getPathToOutputFile());

    createDropDownList(currentEnvironment.getName());


    save.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (Setup.this.environmentName == null ||
            Setup.this.environmentName.getText().equals(""))
        {
          JOptionPane dialog = new JOptionPane();
          dialog.showMessageDialog(null, "Environment name can not be blank");
          return;
        }
        if (Setup.this.firstLogFolder == null ||
            Setup.this.firstLogFolder.getText().equals(""))
        {
          JOptionPane dialog = new JOptionPane();
          dialog.showMessageDialog(null, "First log folder can not be blank");
          return;
        }
        Environment environment = new Environment(Setup.this.environmentName.getText(),
                                                  Setup.this.firstLogFolder.getText(),
                                                  Setup.this.optionalLogFolder.getText(),
                                                  Setup.this.pathToOutputFile.getText());
        try {
          Setup.this.environmentManager.saveEnvironment(environment);
          Setup.this.createDropDownList(environment.getName());
        }
        catch (IOException e1) {
          JOptionPane dialog = new JOptionPane();
          dialog.showMessageDialog(null, "Could not persist the environment info: " + e1.getMessage());
          e1.printStackTrace();
          return;
        }
      }
    });
  }


  private void createDropDownList(String currentEnvironmentName) {
    List envList = environmentManager.getEnvironmentNames();
    String[] names = (String[])envList.toArray(new String[0]);
    ComboBoxModel comboModel = new DefaultComboBoxModel(names);
    environmentList.setModel(comboModel);
    environmentList.setSelectedItem(currentEnvironmentName);
  }
}
