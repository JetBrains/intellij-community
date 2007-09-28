package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class ProjectNameWithTypeStep extends ProjectNameStep {
  private JEditorPane myModuleDescriptionPane;
  private JList myTypesList;
  private JCheckBox myCreateModuleCb;
  private JPanel myModulePanel;
  private JPanel myInternalPanel;
  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;

  private boolean myModuleNameChangedByUser = false;
  private boolean myModuleNameDocListenerEnabled = true;

  private boolean myContentRootChangedByUser = false;
  private boolean myContentRootDocListenerEnabled = true;

  private boolean myImlLocationChangedByUser = false;
  private boolean myImlLocationDocListenerEnabled = true;


  public ProjectNameWithTypeStep(WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    super(wizardContext, sequence, mode);
    myAdditionalContentPanel.add(myModulePanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    myCreateModuleCb.setVisible(myWizardContext.isCreatingNewProject());
    myCreateModuleCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        UIUtil.setEnabled(myInternalPanel, myCreateModuleCb.isSelected(), true);
        fireStateChanged();
      }
    });
    myCreateModuleCb.setSelected(true);
    if (!myWizardContext.isCreatingNewProject()){
      myInternalPanel.setBorder(null);
    }
    myModuleDescriptionPane.setContentType(UIUtil.HTML_MIME);
    myModuleDescriptionPane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          try {
            BrowserUtil.launchBrowser(e.getURL().toString());
          }
          catch (IllegalThreadStateException ex) {
            // it's nnot a problem
          }
        }
      }
    });
    myModuleDescriptionPane.setEditable(false);

    ModuleType[] allModuleTypes = ModuleTypeManager.getInstance().getRegisteredTypes();
    final DefaultListModel defaultListModel = new DefaultListModel();
    for (ModuleType moduleType : allModuleTypes) {
      defaultListModel.addElement(moduleType);
    }
    myTypesList.setModel(defaultListModel);
    myTypesList.setSelectionModel(new PermanentSingleSelectionModel());
    myTypesList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final ModuleType moduleType = (ModuleType)value;
        setIcon(moduleType.getBigIcon());
        setDisabledIcon(moduleType.getBigIcon());
        setText(moduleType.getName());
        return rendererComponent;
      }
    });
    myTypesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }

        final ModuleType typeSelected = (ModuleType)myTypesList.getSelectedValue();
        //noinspection HardCodedStringLiteral
        myModuleDescriptionPane.setText("<html><body><font face=\"verdana\" size=\"-1\">" + typeSelected.getDescription() + "</font></body></html>");

        fireStateChanged();
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            myTypesList.requestFocusInWindow();
          }
        });
      }
    });
    myTypesList.setSelectedIndex(0);
    myTypesList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          //todo
        }
      }
    });

    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myModuleNameDocListenerEnabled) {
          myModuleNameChangedByUser = true;
        }
      }
    });
    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myModuleNameChangedByUser) {
          setModuleName(myNamePathComponent.getNameValue());
        }
      }
    });
    setModuleName(myNamePathComponent.getNameValue());

    myModuleContentRoot.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.content.root.chooser.title"), ProjectBundle.message("project.new.wizard.module.content.root.chooser.description"),
                                                myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    myModuleContentRoot.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myContentRootDocListenerEnabled) {
          myContentRootChangedByUser = true;
        }
      }
    });
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myContentRootChangedByUser) {
          setModuleContentRoot(myNamePathComponent.getPath());
        }
      }
    });
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myContentRootChangedByUser) {
          final String path = myModuleContentRoot.getText();
          final int lastSeparatorIndex = path.lastIndexOf(File.separator);
          if (lastSeparatorIndex >= 0) {
            setModuleContentRoot(path.substring(0, lastSeparatorIndex + 1) + myModuleName.getText());
          }
        }
      }
    });
    setModuleContentRoot(myNamePathComponent.getPath());

    myModuleFileLocation.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.file.chooser.title"), ProjectBundle.message("project.new.wizard.module.file.description"),
                                                 myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    myModuleFileLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myImlLocationDocListenerEnabled) {
          myImlLocationChangedByUser = true;
        }
      }
    });
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myNamePathComponent.getPath());
        }
      }
    });
    myModuleContentRoot.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myModuleContentRoot.getText());
        }
      }
    });
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          final String path = myModuleFileLocation.getText();
          final int lastSeparatorIndex = path.lastIndexOf(File.separator);
          if (lastSeparatorIndex >= 0) {
            setImlFileLocation(path.substring(0, lastSeparatorIndex + 1) + myModuleName.getText());
          }
        }
      }
    });
    setImlFileLocation(myNamePathComponent.getPath());
  }

  private void setImlFileLocation(final String path) {
    myImlLocationDocListenerEnabled = false;
    myModuleFileLocation.setText(FileUtil.toSystemDependentName(path));
    myImlLocationDocListenerEnabled = true;
  }

  private void setModuleContentRoot(final String path) {
    myContentRootDocListenerEnabled = false;
    myModuleContentRoot.setText(FileUtil.toSystemDependentName(path));
    myContentRootDocListenerEnabled = true;
  }

  private void setModuleName(String moduleName) {
    myModuleNameDocListenerEnabled = false;
    myModuleName.setText(moduleName);
    myModuleNameDocListenerEnabled = true;
  }

  public void updateStep() {
    super.updateStep();
    if (myCreateModuleCb.isSelected()) {
      mySequence.setType(((ModuleType)myTypesList.getSelectedValue()).getId());
    } else {
      mySequence.setType(null);
    }
  }

  public void updateDataModel() {
    if (myCreateModuleCb.isSelected()) {
      mySequence.setType(((ModuleType)myTypesList.getSelectedValue()).getId());
      super.updateDataModel();
      final ModuleBuilder builder = (ModuleBuilder)myMode.getModuleBuilder();
      assert builder != null;
      builder.setName(myModuleName.getText());
      builder.setModuleFilePath(FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + myModuleName.getText() + ".iml");
      ((SourcePathsBuilder)builder).setContentEntryPath(FileUtil.toSystemIndependentName(myModuleContentRoot.getText()));
    } else {
      mySequence.setType(null);
      super.updateDataModel();
    }
  }

  public boolean validate() throws ConfigurationException {
    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.file"), myModuleFileLocation.getText(), myImlLocationChangedByUser)) {
      return false;
    }
    if (!ProjectWizardUtil
      .createDirectoryIfNotExists(IdeBundle.message("directory.module.content.root"), myModuleContentRoot.getText(), myContentRootChangedByUser)) {
      return false;
    }
    return super.validate();
  }

  public void disposeUIResources() {
    super.disposeUIResources();
  }

  private static class PermanentSingleSelectionModel extends DefaultListSelectionModel {
    public PermanentSingleSelectionModel() {
      super.setSelectionMode(SINGLE_SELECTION);
    }

    public final void setSelectionMode(int selectionMode) {
    }

    public final void removeSelectionInterval(int index0, int index1) {
    }
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch";
  }
}