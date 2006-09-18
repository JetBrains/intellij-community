/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.Chunk;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.Alarm;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ProjectConfigurable extends NamedConfigurable<Project> {

  private final Project myProject;

  private static final Icon PROJECT_ICON = IconLoader.getIcon("/nodes/project.png");

  private boolean myStartModuleWizardOnShow;
  private LanguageLevelCombo myLanguageLevelCombo;
  private ProjectJdkConfigurable myProjectJdkConfigurable;
  private JRadioButton myRbRelativePaths;

  @SuppressWarnings({"FieldCanBeLocal"})
  private JRadioButton myRbAbsolutePaths;

  private FieldPanel myProjectCompilerOutput;

  private ProjectConfigurable.MyJPanel myPanel;

  private Alarm myUpdateWarningAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private JLabel myWarningLabel = new JLabel("");
  private ModulesConfigurator myModulesConfigurator;
  private JPanel myWholePanel;

  public ProjectConfigurable(Project project, ModulesConfigurator configurator, ProjectJdksModel model) {
    myProject = project;
    myModulesConfigurator = configurator;
    init(model);
  }


  public JComponent createOptionsPanel() {
    myProjectJdkConfigurable.createComponent(); //reload changed jdks
    return myPanel;
  }

  private void init(final ProjectJdksModel model) {
    myPanel = new ProjectConfigurable.MyJPanel();
    myPanel.setPreferredSize(new Dimension(700, 500));

    myRbRelativePaths.setText(ProjectBundle.message("module.paths.outside.module.dir.relative.radio"));
    myRbAbsolutePaths.setText(ProjectBundle.message("module.paths.outside.module.dir.absolute.radio"));

    if (((ProjectEx)myProject).isSavePathsRelative()) {
      myRbRelativePaths.setSelected(true);
    }
    else {
      myRbAbsolutePaths.setSelected(true);
    }

    myProjectJdkConfigurable = new ProjectJdkConfigurable(myProject, model);
    myPanel.add(myProjectJdkConfigurable.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0,
                                                                                   GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                                                   new Insets(4, 4, 0, 0), 0, 0));

    myPanel.add(myWholePanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));

    //myWarningLabel.setUI(new MultiLineLabelUI());
    myPanel.add(myWarningLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                       GridBagConstraints.BOTH, new Insets(10, 6, 10, 0), 0, 0));
  }

  public void disposeUIResources() {
    if (myProjectJdkConfigurable != null) {
      myProjectJdkConfigurable.disposeUIResources();
    }
  }

  public void reset() {
    myProjectJdkConfigurable.reset();
    final String compilerOutput = ProjectRootManagerEx.getInstance(myProject).getCompilerOutputUrl();
    if (compilerOutput != null) {
      myProjectCompilerOutput.setText(FileUtil.toSystemDependentName(VfsUtil.urlToPath(compilerOutput)));
    }
    myLanguageLevelCombo.reset(myProject);
    updateCircularDependencyWarning();
  }

  void updateCircularDependencyWarning() {
    myUpdateWarningAlarm.cancelAllRequests();
    myUpdateWarningAlarm.addRequest(new Runnable() {
      public void run() {
        final Graph<Chunk<ModifiableRootModel>> graph = ModuleCompilerUtil.toChunkGraph(myModulesConfigurator.createGraphGenerator());
        final Collection<Chunk<ModifiableRootModel>> chunks = graph.getNodes();
        String cycles = "";
        int count = 0;
        for (Chunk<ModifiableRootModel> chunk : chunks) {
          final Set<ModifiableRootModel> modules = chunk.getNodes();
          String cycle = "";
          for (ModifiableRootModel model : modules) {
            cycle += ", " + model.getModule().getName();
          }
          if (modules.size() > 1) {
            @NonNls final String br = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";
            cycles += br + (++count) + ". " + cycle.substring(2);            
          }
        }
        @NonNls final String leftBrace = "<html>";
        @NonNls final String rightBrace = "</html>";
        final String warningMessage =
          leftBrace + (count > 0 ? ProjectBundle.message("module.circular.dependency.warning", cycles, count) : "") + rightBrace;
        final int count1=count;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myWarningLabel.setIcon(count1 > 0 ? Messages.getWarningIcon() : null);
            myWarningLabel.setText(warningMessage);
            myWarningLabel.repaint();}
          }
        );
      }
    }, 300);
  }


  public void apply() throws ConfigurationException {
    final ProjectRootManagerImpl projectRootManager = ProjectRootManagerImpl.getInstanceImpl(myProject);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final LanguageLevel newLevel = (LanguageLevel)myLanguageLevelCombo.getSelectedItem();
        projectRootManager.setLanguageLevel(newLevel);
        ((ProjectEx)myProject).setSavePathsRelative(myRbRelativePaths.isSelected());
        try {
          myProjectJdkConfigurable.apply();
        }
        catch (ConfigurationException e) {
          //cant't be
        }
        String canonicalPath = myProjectCompilerOutput.getText();
        if (canonicalPath != null && canonicalPath.length() > 0) {
          try {
            canonicalPath = new File(canonicalPath).getCanonicalPath();
          }
          catch (IOException e) {
            //file doesn't exist yet
          }
          canonicalPath = FileUtil.toSystemIndependentName(canonicalPath);
          projectRootManager.setCompilerOutputUrl(VfsUtil.pathToUrl(canonicalPath));
        }
        else {
          projectRootManager.setCompilerOutputPointer(null);
        }
      }
    });
  }


  public void setDisplayName(final String name) {
    //do nothing
  }

  public Project getEditableObject() {
    return myProject;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.project.banner.text", myProject.getName());
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.project.display.name", myProject.getName());
  }

  public Icon getIcon() {
    return PROJECT_ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() { //todo help id
    return null;
  }


  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isModified() {
    final ProjectRootManagerEx projectRootManagerEx = ProjectRootManagerEx.getInstanceEx(myProject);
    if (!projectRootManagerEx.getLanguageLevel().equals(myLanguageLevelCombo.getSelectedItem())) {
      return true;
    }
    final String compilerOutput = projectRootManagerEx.getCompilerOutputUrl();
    if (!Comparing.strEqual(FileUtil.toSystemIndependentName(VfsUtil.urlToPath(compilerOutput)),
                            FileUtil.toSystemIndependentName(myProjectCompilerOutput.getText()))) return true;
    if (myProjectJdkConfigurable.isModified()) return true;
    return (((ProjectEx)myProject).isSavePathsRelative() != myRbRelativePaths.isSelected());
  }

  private void createUIComponents() {
    myLanguageLevelCombo = new LanguageLevelCombo();
    final JTextField textField = new JTextField();
    final FileChooserDescriptor outputPathsChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    outputPathsChooserDescriptor.setHideIgnored(false);
    myProjectCompilerOutput = new FieldPanel(textField, null,
                                             null,
                                             new BrowseFilesListener(textField,
                                                                     "",
                                                                     ProjectBundle.message("project.compiler.output"),
                                                                     outputPathsChooserDescriptor),
                                             new Runnable() {
                                                public void run() {
                                                  //do nothing
                                                }
                                              });
  }

  public void setStartModuleWizardOnShow(final boolean show) {
    myStartModuleWizardOnShow = show;
  }

  private class MyJPanel extends JPanel {
    public MyJPanel() {
      super(new GridBagLayout());
    }

    public void addNotify() {
      super.addNotify();
      if (myStartModuleWizardOnShow) {
        final Window parentWindow = (Window)SwingUtilities.getAncestorOfClass(Window.class, this);
        parentWindow.addWindowListener(new WindowAdapter() {
          public void windowActivated(WindowEvent e) {
            parentWindow.removeWindowListener(this);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                final ModuleBuilder moduleBuilder = myModulesConfigurator.runModuleWizard(parentWindow);
                if (moduleBuilder != null) {
                  myModulesConfigurator.addModule(moduleBuilder);
                }
              }
            });
          }
        });
      }
    }
  }

}
