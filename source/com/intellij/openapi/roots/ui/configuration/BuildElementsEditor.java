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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;

public class BuildElementsEditor extends ModuleElementsEditor {
  private static final Icon ICON = IconLoader.getIcon("/modules/output.png");
  private JRadioButton myInheritCompilerOutput;
  @SuppressWarnings({"FieldCanBeLocal"})
  private JRadioButton myPerModuleCompilerOutput;

  private CommitableFieldPanel myOutputPathPanel;
  private CommitableFieldPanel myTestsOutputPathPanel;
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

    myOutputPathPanel = createOutputPathPanel(ProjectBundle.message("module.paths.output.title"), new CommitPathRunnable() {
      public void saveUrl(String url) {
        if (myModel.isCompilerOutputPathInherited()) return;  //do not override settings if any
        myModel.setCompilerOutputPath(url);
      }
    });
    myTestsOutputPathPanel = createOutputPathPanel(ProjectBundle.message("module.paths.test.output.title"), new CommitPathRunnable() {
      public void saveUrl(String url) {
        if (myModel.isCompilerOutputPathInherited()) return; //do not override settings if any
        myModel.setCompilerOutputPathForTests(url);
      }
    });

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
    final boolean outputPathInherited = myModel.isCompilerOutputPathInherited();
    myInheritCompilerOutput.setSelected(outputPathInherited);
    myPerModuleCompilerOutput.setSelected(!outputPathInherited);
    enableCompilerSettings(!outputPathInherited);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder(ProjectBundle.message("project.roots.output.compiler.title")));
    panel.add(outputPathsPanel, BorderLayout.NORTH);
    return panel;
  }

  private void updateOutputPathPresentation() {
    final VirtualFile compilerOutputPath = myModel.getCompilerOutputPath();
    if (compilerOutputPath != null) {
      myOutputPathPanel.setText(compilerOutputPath.getPath().replace('/', File.separatorChar));
    }
    else {
      final String compilerOutputUrl = myModel.getCompilerOutputPathUrl();
      if (compilerOutputUrl != null) {
        myOutputPathPanel.setText(VirtualFileManager.extractPath(compilerOutputUrl).replace('/', File.separatorChar));
      }
    }

    final VirtualFile testsOutputPath = myModel.getCompilerOutputPathForTests();
    if (testsOutputPath != null) {
      myTestsOutputPathPanel.setText(testsOutputPath.getPath().replace('/', File.separatorChar));
    }
    else {
      final String testsOutputUrl = myModel.getCompilerOutputPathForTestsUrl();
      if (testsOutputUrl != null) {
        myTestsOutputPathPanel.setText(VirtualFileManager.extractPath(testsOutputUrl).replace('/', File.separatorChar));
      }
    }
  }

  private void enableCompilerSettings(final boolean enabled) {
    UIUtil.setEnabled(myOutputPathPanel, enabled, true);
    UIUtil.setEnabled(myOutputLabel, enabled, true);
    UIUtil.setEnabled(myTestsOutputPathPanel, enabled, true);
    UIUtil.setEnabled(myTestOutputLabel, enabled, true);
    myCbExcludeOutput.setEnabled(enabled);
    myModel.inheritCompilerOutputPath(!enabled);
    updateOutputPathPresentation();
  }

  private CommitableFieldPanel createOutputPathPanel(final String title, final CommitPathRunnable commitPathRunnable) {
    final JTextField textField = new JTextField();
    final FileChooserDescriptor outputPathsChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    outputPathsChooserDescriptor.setHideIgnored(false);
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);

    final Runnable commitRunnable = new Runnable() {
      public void run() {
        if (!myModel.isWritable()) {
          return;
        }
        final String path = textField.getText().trim();
        if (path.length() == 0) {
          commitPathRunnable.saveUrl(null);
        }
        else {
          // should set only absolute paths
          String canonicalPath;
          try {
            canonicalPath = new File(path).getCanonicalPath();
          }
          catch (IOException e) {
            canonicalPath = path;
          }
          commitPathRunnable.saveUrl(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(canonicalPath)));
        }
      }
    };

    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        commitRunnable.run();
      }
    });

    return new CommitableFieldPanel(textField, null, null, new BrowseFilesListener(textField, title, "", outputPathsChooserDescriptor) {
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        commitRunnable.run();
      }
    }, null, commitRunnable);
  }

  public void saveData() {
    myOutputPathPanel.commit();
    myTestsOutputPathPanel.commit();
  }

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

  private static interface CommitPathRunnable {
    void saveUrl(String url);
  }

  private static class CommitableFieldPanel extends FieldPanel {
    private Runnable myCommitRunnable;

    public CommitableFieldPanel(final JTextField textField,
                                String labelText,
                                final String viewerDialogTitle,
                                ActionListener browseButtonActionListener,
                                final Runnable documentListener,
                                final Runnable commitPathRunnable) {
      super(textField, labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
      myCommitRunnable = commitPathRunnable;
    }

    public void commit() {
      myCommitRunnable.run();
    }
  }
}
