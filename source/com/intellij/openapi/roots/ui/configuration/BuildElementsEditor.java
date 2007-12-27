/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 28-Jun-2006
 * Time: 19:03:32
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class BuildElementsEditor extends ModuleElementsEditor {
  private static final Icon ICON = IconLoader.getIcon("/modules/output.png");
  private JRadioButton myInheritCompilerOutput;
  @SuppressWarnings({"FieldCanBeLocal"})
  private JRadioButton myPerModuleCompilerOutput;

  private FieldPanel myOutputPathPanel;
  private FieldPanel myTestsOutputPathPanel;
  private JCheckBox myCbExcludeOutput;
  private JLabel myOutputLabel;
  private JLabel myTestOutputLabel;

  protected BuildElementsEditor(final Project project, final ModifiableRootModel model) {
    super(project, model);
  }

  public JComponent createComponentImpl() {
    myInheritCompilerOutput = new JRadioButton(ProjectBundle.message("project.inherit.compile.output.path"));
    myPerModuleCompilerOutput = new JRadioButton(ProjectBundle.message("project.module.compile.output.path"));
    ButtonGroup group = new ButtonGroup();
    group.add(myInheritCompilerOutput);
    group.add(myPerModuleCompilerOutput);

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enableCompilerSettings(!myInheritCompilerOutput.isSelected());
      }
    };

    myInheritCompilerOutput.addActionListener(listener);
    myPerModuleCompilerOutput.addActionListener(listener);

    myOutputPathPanel = createOutputPathPanel(ProjectBundle.message("module.paths.output.title"));
    myTestsOutputPathPanel = createOutputPathPanel(ProjectBundle.message("module.paths.test.output.title"));

    myCbExcludeOutput = new JCheckBox(ProjectBundle.message("module.paths.exclude.output.checkbox"), myModel.isExcludeOutput());
    myCbExcludeOutput.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        myModel.setExcludeOutput(e.getStateChange() == ItemEvent.SELECTED);
      }
    });

    final JPanel outputPathsPanel = new JPanel(new GridBagLayout());


    outputPathsPanel.add(myInheritCompilerOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0,
                                                                         GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                                         new Insets(6, 0, 0, 4), 0, 0));
    outputPathsPanel.add(myPerModuleCompilerOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0,
                                                                           GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                                           new Insets(6, 0, 0, 4), 0, 0));

    myOutputLabel = new JLabel(ProjectBundle.message("module.paths.output.label"));
    outputPathsPanel.add(myOutputLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
                                                               GridBagConstraints.NONE, new Insets(6, 12, 0, 4), 0, 0));
    outputPathsPanel.add(myOutputPathPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST,
                                                                   GridBagConstraints.HORIZONTAL, new Insets(6, 4, 0, 0), 0, 0));

    myTestOutputLabel = new JLabel(ProjectBundle.message("module.paths.test.output.label"));
    outputPathsPanel.add(myTestOutputLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
                                                                   GridBagConstraints.NONE, new Insets(6, 12, 0, 4), 0, 0));
    outputPathsPanel.add(myTestsOutputPathPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0,
                                                                        GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                                        new Insets(6, 4, 0, 0), 0, 0));

    outputPathsPanel.add(myCbExcludeOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST,
                                                                   GridBagConstraints.NONE, new Insets(6, 12, 0, 0), 0, 0));

    // fill with data
    updateOutputPathPresentation();

    //compiler settings
    final boolean outputPathInherited = CompilerModuleExtension.getInstance(myModel.getModule()).isCompilerOutputPathInherited();
    myInheritCompilerOutput.setSelected(outputPathInherited);
    myPerModuleCompilerOutput.setSelected(!outputPathInherited);
    enableCompilerSettings(!outputPathInherited);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder(ProjectBundle.message("project.roots.output.compiler.title")));
    panel.add(outputPathsPanel, BorderLayout.NORTH);
    return panel;
  }

  public boolean isModified() {
    CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(myModel.getModule());
    if (compilerModuleExtension.isCompilerOutputPathInherited() != myInheritCompilerOutput.isSelected()) return true;
    if (!Comparing.strEqual(compilerModuleExtension.getCompilerOutputUrl(), FileUtil.toSystemIndependentName(myOutputPathPanel.getText()))) return true;
    if (!Comparing.strEqual(compilerModuleExtension.getCompilerOutputUrlForTests(), FileUtil.toSystemIndependentName(myTestsOutputPathPanel.getText()))) return true;
    return super.isModified();
  }

  public void apply() throws ConfigurationException {
    super.apply();
    CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(myModel.getModule());
    compilerModuleExtension.inheritCompilerOutputPath(myInheritCompilerOutput.isSelected());
    compilerModuleExtension.setCompilerOutputPath(FileUtil.toSystemIndependentName(myOutputPathPanel.getText()));
    compilerModuleExtension.setCompilerOutputPathForTests(FileUtil.toSystemIndependentName(myTestsOutputPathPanel.getText()));
  }

  private void updateOutputPathPresentation() {
    CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(myModel.getModule());
    if (compilerModuleExtension.isCompilerOutputPathInherited()) {
      final String baseUrl = ProjectStructureConfigurable.getInstance(myProject).getProjectConfig().getCompilerOutputUrl();
      moduleCompileOutputChanged(baseUrl, myModel.getModule().getName());
    }
    else {
      final String compilerOutputUrl = compilerModuleExtension.getCompilerOutputUrl();
      if (compilerOutputUrl != null) {
        myOutputPathPanel.setText(FileUtil.toSystemDependentName(compilerOutputUrl));
      }
      final String compilerOutputUrlForTests = compilerModuleExtension.getCompilerOutputUrlForTests();
      if (compilerOutputUrlForTests != null) {
        myTestsOutputPathPanel.setText(FileUtil.toSystemDependentName(compilerOutputUrlForTests));
      }
    }
  }

  private void enableCompilerSettings(final boolean enabled) {
    UIUtil.setEnabled(myOutputPathPanel, enabled, true);
    UIUtil.setEnabled(myOutputLabel, enabled, true);
    UIUtil.setEnabled(myTestsOutputPathPanel, enabled, true);
    UIUtil.setEnabled(myTestOutputLabel, enabled, true);
    myCbExcludeOutput.setEnabled(enabled);
    updateOutputPathPresentation();
  }

  private static FieldPanel createOutputPathPanel(final String title) {
    final JTextField textField = new JTextField();
    final FileChooserDescriptor outputPathsChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    outputPathsChooserDescriptor.setHideIgnored(false);
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    return new FieldPanel(textField, null, null, new BrowseFilesListener(textField, title, "", outputPathsChooserDescriptor), null);
  }

  public void saveData() {}

  public String getDisplayName() {
    return ProjectBundle.message("output.tab.title");
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.structureModulesPage.outputJavadoc";
  }


  public void moduleStateChanged() {
    //if content enties tree was changed
    myCbExcludeOutput.setSelected(myModel.isExcludeOutput());
  }

  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName) {
    if (myInheritCompilerOutput.isSelected()) {
      if (baseUrl != null) {
        myOutputPathPanel.setText(FileUtil.toSystemDependentName(VfsUtil.urlToPath(baseUrl + "/" + CompilerModuleExtension
          .PRODUCTION + "/" + moduleName)));
        myTestsOutputPathPanel.setText(FileUtil.toSystemDependentName(VfsUtil.urlToPath(baseUrl + "/" + CompilerModuleExtension
          .TEST + "/" + moduleName)));
      }
      else {
        myOutputPathPanel.setText(null);
        myTestsOutputPathPanel.setText(null);
      }
    }
  }

  private static interface CommitPathRunnable {
    void saveUrl(String url);
  }
}
