package com.jetbrains.python.packaging.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageService;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

public class ManageRepoDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JBList myList;
  private boolean myEnabled;

  public ManageRepoDialog() {
    super(false);
    init();
    setTitle("Manage Repositories");
    final DefaultListModel repoModel = new DefaultListModel();
    repoModel.addElement(PyPIPackageUtil.PYPI_URL);

    for (String url : PyPackageService.getInstance().additionalRepositories) {
      repoModel.addElement(url);
    }
    myList = new JBList();
    myList.setModel(repoModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        final Object selected = myList.getSelectedValue();
        myEnabled = !PyPIPackageUtil.PYPI_URL.equals(selected);
      }
    });

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList).disableUpDownActions();
    decorator.setAddActionName("Add repository");
    decorator.setRemoveActionName("Remove repository from list");
    decorator.setEditActionName("Edit repository URL");

    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        String url = Messages.showInputDialog("Please input repository URL", "Repository URL", null);
        if (!repoModel.contains(url) && !StringUtil.isEmptyOrSpaces(url)) {
          repoModel.addElement(url);
          PyPackageService.getInstance().addRepository(url);
        }
      }
    });
    decorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final String oldValue = (String)myList.getSelectedValue();

        String url = Messages.showInputDialog("Please edit repository URL", "Repository URL", null, oldValue, new InputValidator() {
          @Override
          public boolean checkInput(String inputString) {
            return !repoModel.contains(inputString);
          }

          @Override
          public boolean canClose(String inputString) {
            return true;
          }
        });
        if (!StringUtil.isEmptyOrSpaces(url) && !oldValue.equals(url)) {
          repoModel.addElement(url);
          repoModel.removeElement(oldValue);
          PyPackageService.getInstance().removeRepository(oldValue);
          PyPackageService.getInstance().addRepository(url);
        }
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        String selected = (String)myList.getSelectedValue();
        PyPackageService.getInstance().removeRepository(selected);
        repoModel.removeElement(selected);
        button.setEnabled(false);
      }
    });
    decorator.setRemoveActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        return myEnabled;
      }
    });
    decorator.setEditActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        return myEnabled;
      }
    });

    final JPanel panel = decorator.createPanel();
    panel.setPreferredSize(new Dimension(800, 600));
    myMainPanel.add(panel);

  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
