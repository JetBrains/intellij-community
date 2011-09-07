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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.FileContentUtil;
import com.jetbrains.django.lang.template.DjangoTemplateFileType;
import com.jetbrains.mako.TemplatesConfigurationsModel;
import com.jetbrains.mako.TemplatesService;
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
public class PyIntegratedToolsConfigurable implements Configurable, NonDefaultProjectConfigurable {
  private JPanel myMainPanel;
  private JComboBox myTestRunnerComboBox;
  private JComboBox myDocstringFormatComboBox;
  private PythonTestConfigurationsModel myModel;
  private TemplatesConfigurationsModel myTemplatesModel;
  private Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;
  private TextFieldWithBrowseButton myWorkDir;
  private JCheckBox txtIsRst;
  private JComboBox myTemplateLanguage;

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

    List<String> templateConfigurations = TemplatesService.getInstance(myProject).getConfigurations();
    myTemplatesModel = new TemplatesConfigurationsModel(templateConfigurations, TemplatesService.getInstance(myProject).getProjectConfiguration(),
                                                myProject);
    updateConfigurations();
    return myMainPanel;
  }

  private void updateConfigurations() {
    myTestRunnerComboBox.setModel(myModel);
    myTemplateLanguage.setModel(myTemplatesModel);
  }

  @Override
  public boolean isModified() {
    if (myTestRunnerComboBox.getSelectedItem() != myModel.getProjectConfiguration()) {
      return true;
    }
    if (myTemplateLanguage.getSelectedItem() != myTemplatesModel.getProjectConfiguration()) {
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

  public static void reparseTemplateFiles(final @NotNull Project project) {
    final List<VirtualFile> templatesToReparse = Lists.newArrayList();
    ProjectRootManager.getInstance(project).getFileIndex().iterateContent(new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        if (!fileOrDir.isDirectory() && DjangoTemplateFileType.getPossibleExtensions().contains(fileOrDir.getExtension())) {
          templatesToReparse.add(fileOrDir);
        }
        return true;
      }
    });
    FileContentUtil.reparseFiles(project, templatesToReparse, true);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {

        for (Editor editor : EditorFactoryImpl.getInstance().getAllEditors()) {
          if (editor instanceof EditorEx) {
            final VirtualFile vFile = ((EditorEx)editor).getVirtualFile();
            if (vFile != null) {
              final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, vFile);
              ((EditorEx)editor).setHighlighter(highlighter);
            }
          }
        }
      }
    });

    DaemonCodeAnalyzer.getInstance(project).restart();
  }


  @Override
  public void apply() throws ConfigurationException {
    myModel.apply();
    boolean needReparse = false;
    if (myTemplateLanguage.getSelectedItem() != myTemplatesModel.getProjectConfiguration())
      needReparse = true;
    myTemplatesModel.apply();
    myDocumentationSettings.myDocStringFormat = (String) myDocstringFormatComboBox.getSelectedItem();
    ReSTService.getInstance(myProject).setWorkdir(myWorkDir.getText());
    ReSTService.getInstance(myProject).setTxtIsRst(txtIsRst.isSelected());
    if (needReparse)
      reparseTemplateFiles(myProject);
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getProjectConfiguration());
    myTemplateLanguage.setSelectedItem(myTemplatesModel.getProjectConfiguration());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myTemplatesModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.myDocStringFormat);
    myWorkDir.setText(ReSTService.getInstance(myProject).getWorkdir());
    txtIsRst.setSelected(ReSTService.getInstance(myProject).txtIsRst());
  }

  @Override
  public void disposeUIResources() {
  }
}

