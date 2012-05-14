package com.jetbrains.python.packaging.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageService;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ManageRepoDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JBList myList;
  private JButton myAddButton;
  private JButton myRemoveButton;

  public ManageRepoDialog() {
    super(false);
    init();
    setTitle("Manage Repositories");
    final DefaultListModel repoModel = new DefaultListModel();
    repoModel.addElement(PyPIPackageUtil.INSTANCE.PYPI_URL);

    for (String url : PyPackageService.getInstance().additionalRepositories) {
      repoModel.addElement(url);
    }
    
    myList.setModel(repoModel);
    
    myAddButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        String url = Messages.showInputDialog("Please input repository URL", "Repository URL", null);
        if (!StringUtil.isEmptyOrSpaces(url)) {
          repoModel.addElement(url);
          PyPackageService.getInstance().addRepository(url);
        }
      }
    });
    myRemoveButton.setEnabled(false);
    
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        Object selected = myList.getSelectedValue();
        myRemoveButton.setEnabled(!PyPIPackageUtil.INSTANCE.PYPI_URL.equals(selected));
      }
    });

    myRemoveButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        String selected = (String)myList.getSelectedValue();
        PyPackageService.getInstance().removeRepository(selected);
        repoModel.removeElement(selected);
        myRemoveButton.setEnabled(false);
      }
    });
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
