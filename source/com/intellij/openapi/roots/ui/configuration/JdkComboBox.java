/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.projectWizard.ProjectJdkListRenderer;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2005
 */
class JdkComboBox extends JComboBox{
  public JdkComboBox(final ProjectJdksModel jdksModel) {
    super(new JdkComboBoxModel(jdksModel));
    setRenderer(new ProjectJdkListRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (JdkComboBox.this.isEnabled()) {
          if (value instanceof InvalidJdkComboBoxItem) {
            final String str = value.toString();
            append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
          }
          else if (value instanceof ProjectJdkComboBoxItem){
            final ProjectJdkComboBoxItem item = (ProjectJdkComboBoxItem)value;
            final String str = item.toString();
            final ProjectJdk jdk = jdksModel.getProjectJdk();
            if (jdk != null){
              setIcon(jdk.getSdkType().getIcon());
              append(str, SimpleTextAttributes.REGULAR_ATTRIBUTES);
              append(" (" + jdk.getName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else {
              append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
          }
          else {
            super.customizeCellRenderer(list, value != null ? ((JdkComboBoxItem)value).getJdk() : new NoneJdkComboBoxItem(), index, selected, hasFocus);
          }
        }
      }
    });
  }

  public JButton createSetupButton(final Project project, final ProjectJdksModel jdksModel, final JdkComboBoxItem firstItem) {
    return createSetupButton(project, jdksModel, firstItem, null, false);
  }


  public JButton createSetupButton(final Project project,
                                   final ProjectJdksModel jdksModel,
                                   final JdkComboBoxItem firstItem,
                                   final Condition<ProjectJdk> additionalSetup,
                                   final boolean moduleJdkSetup) {
    final FixedSizeButton setUpButton = new FixedSizeButton(this);
    setUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ProjectRootConfigurable configurable = ProjectRootConfigurable.getInstance(project);
        DefaultActionGroup group = new DefaultActionGroup();
        jdksModel.createAddActions(group, JdkComboBox.this, new Consumer<ProjectJdk>() {
          public void consume(final ProjectJdk jdk) {
            configurable.addJdkNode(jdk);
            reloadModel(firstItem, project);
            setSelectedJdk(jdk); //restore selection
            if (additionalSetup != null) {
              if (additionalSetup.value(jdk)) { //leave old selection
                setSelectedJdk(firstItem.getJdk());
              }
            }
          }
        });
        JBPopupFactory.getInstance()
          .createActionGroupPopup(ProjectBundle.message("project.roots.set.up.jdk.title", moduleJdkSetup ? 1 : 2), group,
                                  DataManager.getInstance().getDataContext(), JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
          .showUnderneathOf(setUpButton);
      }
    });
    ComponentWithBrowseButton.MyDoClickAction.addTo(setUpButton, this);
    return setUpButton;
  }

  public JdkComboBoxItem getSelectedItem() {
    return (JdkComboBoxItem)super.getSelectedItem();
  }

  public ProjectJdk getSelectedJdk() {
    final JdkComboBoxItem selectedItem = (JdkComboBoxItem)super.getSelectedItem();
    return selectedItem != null? selectedItem.getJdk() : null;
  }

  public void setSelectedJdk(ProjectJdk jdk) {
    final int index = indexOf(jdk);
    if (index >= 0) {
      setSelectedIndex(index);
    }
  }

  public void setInvalidJdk(String name) {
    removeInvalidElement();
    addItem(new InvalidJdkComboBoxItem(name));
    setSelectedIndex(getModel().getSize() - 1);
  }
  
  private int indexOf(ProjectJdk jdk) {
    final JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final JdkComboBoxItem elementAt = model.getElementAt(idx);
      if (jdk == null) {
        if (elementAt instanceof NoneJdkComboBoxItem) {
          return idx;
        } else if (elementAt instanceof ProjectJdkComboBoxItem){
          return idx;
        }
      }
      else {
        if (jdk.equals(elementAt.getJdk())) {
          return idx;
        }
      }
    }
    return -1;
  }
  
  private void removeInvalidElement() {
    final JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final JdkComboBoxItem elementAt = model.getElementAt(idx);
      if (elementAt instanceof InvalidJdkComboBoxItem) {
        removeItemAt(idx);
        break;
      }
    }
  }

  public void reloadModel(JdkComboBoxItem firstItem, Project project) {
    final DefaultComboBoxModel model = ((DefaultComboBoxModel)getModel());
    model.removeAllElements();
    model.addElement(firstItem);
    final Collection<ProjectJdk> projectJdks = ProjectJdksModel.getInstance(project).getProjectJdks().values();
    for (ProjectJdk projectJdk : projectJdks) {
      model.addElement(new JdkComboBox.JdkComboBoxItem(projectJdk));
    }
  }

  private static class JdkComboBoxModel extends DefaultComboBoxModel {
    public JdkComboBoxModel(final ProjectJdksModel jdksModel) {
      super();
      final Sdk[] jdks = jdksModel.getSdks();
      for (Sdk jdk : jdks) {
        addElement(new JdkComboBoxItem((ProjectJdk)jdk));
      }
    }

    // implements javax.swing.ListModel
    public JdkComboBoxItem getElementAt(int index) {
      return (JdkComboBoxItem)super.getElementAt(index);
    }
  }
  
  public static class JdkComboBoxItem {
    private final ProjectJdk myJdk;

    public JdkComboBoxItem(ProjectJdk jdk) {
      myJdk = jdk;
    }

    public ProjectJdk getJdk() {
      return myJdk;
    }

    public String toString() {
      return myJdk.getName();
    }
  }

  public static class ProjectJdkComboBoxItem extends JdkComboBoxItem {
    public ProjectJdkComboBoxItem() {
      super(null);
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.project.item");
    }
  }

  public static class NoneJdkComboBoxItem extends JdkComboBoxItem {
    public NoneJdkComboBoxItem() {
      super(null);
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.none.item");
    }
  }

  private static class InvalidJdkComboBoxItem extends JdkComboBoxItem {
    private final String myName;

    public InvalidJdkComboBoxItem(String name) {
      super(null);
      myName = ProjectBundle.message("jdk.combo.box.invalid.item", name);
    }

    public String toString() {
      return myName;
    }
  }
  
}
