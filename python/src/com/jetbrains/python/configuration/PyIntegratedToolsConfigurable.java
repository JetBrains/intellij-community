package com.jetbrains.python.configuration;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.FileContentUtil;
import com.jetbrains.django.lang.template.DjangoTemplateFileType;
import com.jetbrains.python.templateLanguages.TemplatesConfigurationsModel;
import com.jetbrains.python.templateLanguages.TemplatesService;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.rest.ReSTService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * User: catherine
 */
public class PyIntegratedToolsConfigurable implements SearchableConfigurable, NonDefaultProjectConfigurable {
  private JPanel myMainPanel;
  private JComboBox myTestRunnerComboBox;
  private JComboBox myDocstringFormatComboBox;
  private PythonTestConfigurationsModel myModel;
  private Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;
  private TextFieldWithBrowseButton myWorkDir;
  private JCheckBox txtIsRst;

  public PyIntegratedToolsConfigurable(Project project) {
    myProject = project;
    myDocumentationSettings = PyDocumentationSettings.getInstance(project);
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel(DocStringFormat.ALL, myDocumentationSettings.myDocStringFormat));

    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myWorkDir.addBrowseFolderListener("Please choose working directory:", null, project, fileChooserDescriptor);
    ReSTService service = ReSTService.getInstance(myProject);
    myWorkDir.setText(service.getWorkdir());
    txtIsRst.setSelected(service.txtIsRst());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Python Integrated Tools";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return "test_runner_configuration";
  }

  @Override
  public JComponent createComponent() {
    List<String> configurations = TestRunnerService.getInstance(myProject).getConfigurations();
    myModel = new PythonTestConfigurationsModel(configurations, TestRunnerService.getInstance(myProject).getProjectConfiguration(),
                                                myProject);

    updateConfigurations();
    return myMainPanel;
  }

  private void updateConfigurations() {
    myTestRunnerComboBox.setModel(myModel);
  }

  @Override
  public boolean isModified() {
    if (myTestRunnerComboBox.getSelectedItem() != myModel.getProjectConfiguration()) {
      return true;
    }
    if (!Comparing.equal(myDocstringFormatComboBox.getSelectedItem(), myDocumentationSettings.myDocStringFormat)) {
      DaemonCodeAnalyzer.getInstance(myProject).restart();
      return true;
    }
    if (!ReSTService.getInstance(myProject).getWorkdir().equals(myWorkDir.getText()))
      return true;
    if (!ReSTService.getInstance(myProject).txtIsRst() == txtIsRst.isSelected())
      return true;
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myModel.apply();
    myDocumentationSettings.myDocStringFormat = (String) myDocstringFormatComboBox.getSelectedItem();
    ReSTService.getInstance(myProject).setWorkdir(myWorkDir.getText());
    ReSTService.getInstance(myProject).setTxtIsRst(txtIsRst.isSelected());
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getProjectConfiguration());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.myDocStringFormat);
    myWorkDir.setText(ReSTService.getInstance(myProject).getWorkdir());
    txtIsRst.setSelected(ReSTService.getInstance(myProject).txtIsRst());
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  @Override
  public String getId() {
    return "PyIntegratedToolsConfigurable";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}

