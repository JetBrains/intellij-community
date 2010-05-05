/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 3, 2005
 * Time: 6:15:22 PM
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.table.TableView;
import com.theoryinpractice.testng.configuration.browser.*;
import com.theoryinpractice.testng.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.TestNG;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Map;

public class TestNGConfigurationEditor extends SettingsEditor<TestNGConfiguration>
{
  //private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private final Project project;

  private JPanel panel;

  private LabeledComponent<TextFieldWithBrowseButton> classField;
  private EnvironmentVariablesComponent envVariablesComponent;
  private LabeledComponent<JComboBox> moduleClasspath;
  private AlternativeJREPanel alternateJDK;
  private final ConfigurationModuleSelector moduleSelector;
  private JRadioButton suiteTest;
  private JRadioButton packageTest;
  private JRadioButton classTest;
  private JRadioButton methodTest;
  private JRadioButton groupTest;
  private final TestNGConfigurationModel model;
  private LabeledComponent<TextFieldWithBrowseButton> methodField;
  private LabeledComponent<TextFieldWithBrowseButton> packageField;
  private LabeledComponent<TextFieldWithBrowseButton> groupField;
  private LabeledComponent<TextFieldWithBrowseButton> suiteField;
  private JRadioButton packagesInProject;
  private JRadioButton packagesInModule;
  private JRadioButton packagesAcrossModules;
  private JPanel packagePanel;
  private TestNGParametersTableModel propertiesTableModel;
  private LabeledComponent<TextFieldWithBrowseButton> propertiesFile;
  private LabeledComponent<TextFieldWithBrowseButton> outputDirectory;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private TableView propertiesTableView;
  private JPanel commonParametersPanel;//temp compilation problems
  private JButton addListener;
  private JList listenersTable;
  private JButton removeListener;
  private LabeledComponent<JComboBox> annotationType;
  private JCheckBox myUseDefaultReportersCheckBox;
  private final CommonJavaParametersPanel commonJavaParameters = new CommonJavaParametersPanel();
  private ArrayList<Map.Entry> propertiesList;
  private TestNGListenersTableModel listenerModel;

  private TestNGConfiguration config;

  public TestNGConfigurationEditor(Project project) {
    this.project = project;
    BrowseModuleValueActionListener[] browseListeners = new BrowseModuleValueActionListener[] {new PackageBrowser(project),
        new TestClassBrowser(project, this), new MethodBrowser(project, this), new GroupBrowser(project, this), new SuiteBrowser(project)};
    model = new TestNGConfigurationModel(project);
    model.setListener(this);
    createView();
    moduleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    registerListener(new JRadioButton[] {packageTest, classTest, methodTest, groupTest, suiteTest}, new ChangeListener()
    {
      public void stateChanged(ChangeEvent e) {
        ButtonModel buttonModel = (ButtonModel) e.getSource();
        if (buttonModel.isSelected()) {
          if (buttonModel == packageTest.getModel()) {
            model.setType(TestType.PACKAGE);
          } else if (buttonModel == classTest.getModel()) {
            model.setType(TestType.CLASS);
          } else if (buttonModel == methodTest.getModel()) {
            model.setType(TestType.METHOD);
          } else if (buttonModel == groupTest.getModel()) {
            model.setType(TestType.GROUP);
          } else if (buttonModel == suiteTest.getModel()) {
            model.setType(TestType.SUITE);
          }
          redisplay();
        }
      }
    });
    registerListener(new JRadioButton[] {packagesInProject, packagesInModule, packagesAcrossModules}, null);
    packagesInProject.addChangeListener(new ChangeListener()
    {
      public void stateChanged(ChangeEvent e) {
        evaluateModuleClassPath();
      }
    });

    LabeledComponent[] components = new LabeledComponent[] {packageField, classField, methodField, groupField, suiteField};
    for (int i = 0; i < components.length; i++) {
      TextFieldWithBrowseButton field = (TextFieldWithBrowseButton) components[i].getComponent();
      Document document = model.getDocument(i);
      field.getTextField().setDocument(document);
      browseListeners[i].setField(field);
    }
    model.setType(TestType.CLASS);
    addListener.addActionListener(new AddTestListenerListener());
    removeListener.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent event) {
        int idx = listenersTable.getSelectedIndex() - 1;
        for (int row : listenersTable.getSelectedIndices()) {
          listenerModel.removeListener(row);
        }
        if (idx > -1) listenersTable.setSelectedIndex(idx);
      }
    });
    propertiesFile.getComponent().getTextField().setDocument(model.getPropertiesFileDocument());
    outputDirectory.getComponent().getTextField().setDocument(model.getOutputDirectoryDocument());

    commonJavaParameters.setProgramParametersLabel(ExecutionBundle.message("junit.configuration.test.runner.parameters.label"));
  }

  private void evaluateModuleClassPath() {
    moduleClasspath.setEnabled(!packagesInProject.isSelected());
  }

  private void redisplay() {
    if (packageTest.isSelected()) {
      packagePanel.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(false);
    } else if (classTest.isSelected()) {
      packagePanel.setVisible(false);
      classField.setVisible(true);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(false);
    } else if (methodTest.isSelected()) {
      packagePanel.setVisible(false);
      classField.setVisible(true);
      methodField.setVisible(true);
      groupField.setVisible(false);
      suiteField.setVisible(false);
    } else if (groupTest.isSelected()) {
      packagePanel.setVisible(false);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(true);
      suiteField.setVisible(false);
    } else if (suiteTest.isSelected()) {
      packagePanel.setVisible(false);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(true);
    }
  }

  public String getClassName() {
    return classField.getComponent().getText();
  }

  public JComboBox getModulesComponent() {
    return moduleClasspath.getComponent();
  }

  @Override
  protected void resetEditorFrom(TestNGConfiguration config) {
    this.config = config;
    model.reset(config);
    commonJavaParameters.reset(config);
    getModuleSelector().reset(config);
    TestData data = config.getPersistantData();
    TestSearchScope scope = data.getScope();
    if (scope == TestSearchScope.SINGLE_MODULE) {
      packagesInModule.setSelected(true);
    } else if (scope == TestSearchScope.MODULE_WITH_DEPENDENCIES) {
      packagesAcrossModules.setSelected(true);
    } else {
      packagesInProject.setSelected(true);
    }
    alternateJDK.init(config.ALTERNATIVE_JRE_PATH, config.ALTERNATIVE_JRE_PATH_ENABLED);
    envVariablesComponent.setEnvs(config.getPersistantData().getEnvs());
    envVariablesComponent.setPassParentEnvs(config.getPersistantData().PASS_PARENT_ENVS);
    propertiesList = new ArrayList<Map.Entry>();
    propertiesList.addAll(data.TEST_PROPERTIES.entrySet());
    propertiesTableModel.setParameterList(propertiesList);

    listenerModel.setListenerList(data.TEST_LISTENERS);
    myUseDefaultReportersCheckBox.setSelected(data.USE_DEFAULT_REPORTERS);


    annotationType.getComponent().setSelectedItem(data.ANNOTATION_TYPE);
  }

  @Override
  public void applyEditorTo(TestNGConfiguration config) {
    model.apply(getModuleSelector().getModule(), config);
    getModuleSelector().applyTo(config);
    TestData data = config.getPersistantData();
    if (packageTest.isSelected()) {
      if (packagesInProject.isSelected()) {
        data.setScope(TestSearchScope.WHOLE_PROJECT);
      } else if (packagesInModule.isSelected()) {
        data.setScope(TestSearchScope.SINGLE_MODULE);
      } else if (packagesAcrossModules.isSelected()) data.setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
    } else {
      data.setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
    }
    commonJavaParameters.applyTo(config);
    config.ALTERNATIVE_JRE_PATH = alternateJDK.getPath();
    config.ALTERNATIVE_JRE_PATH_ENABLED = alternateJDK.isPathEnabled();

    data.ANNOTATION_TYPE = (String) annotationType.getComponent().getSelectedItem();
    data.TEST_PROPERTIES.clear();
    for (Map.Entry<String, String> entry : propertiesList) {
      data.TEST_PROPERTIES.put(entry.getKey(), entry.getValue());
    }

    data.TEST_LISTENERS.clear();
    data.TEST_LISTENERS.addAll(listenerModel.getListenerList());

    data.USE_DEFAULT_REPORTERS = myUseDefaultReportersCheckBox.isSelected();

    data.setEnvs(envVariablesComponent.getEnvs());
    data.PASS_PARENT_ENVS = envVariablesComponent.isPassParentEnvs();
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return moduleSelector;
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return panel;
  }

  private static void registerListener(JRadioButton[] buttons, ChangeListener changelistener) {
    ButtonGroup buttongroup = new ButtonGroup();
    for (JRadioButton button : buttons) {
      button.getModel().addChangeListener(changelistener);
      buttongroup.add(button);
    }

    if (buttongroup.getSelection() == null) buttongroup.setSelected(buttons[0].getModel(), true);
  }

  private void createView() {
    commonParametersPanel.add(commonJavaParameters, BorderLayout.CENTER);

    packageTest.setSelected(false);
    suiteTest.setSelected(false);
    suiteTest.setEnabled(true);
    groupTest.setSelected(false);
    groupTest.setEnabled(true);
    classTest.setSelected(false);
    classTest.setEnabled(true);

    classField.setComponent(new TextFieldWithBrowseButton());
    methodField.setComponent(new TextFieldWithBrowseButton());
    groupField.setComponent(new TextFieldWithBrowseButton());
    suiteField.setComponent(new TextFieldWithBrowseButton());
    packageField.setVisible(true);
    packageField.setEnabled(true);
    packageField.setComponent(new TextFieldWithBrowseButton());

    JComboBox annotationTypeCombo = new JComboBox(new Object[] {"", TestNG.JDK_ANNOTATION_TYPE, TestNG.JAVADOC_ANNOTATION_TYPE});
    annotationType.setComponent(annotationTypeCombo);

    TextFieldWithBrowseButton outputDirectoryButton = new TextFieldWithBrowseButton();
    outputDirectory.setComponent(outputDirectoryButton);
    outputDirectoryButton.addBrowseFolderListener("TestNG", "Select test output directory", project,
                                                  new FileChooserDescriptor(false, true, false, false, false, false));
    moduleClasspath.setEnabled(true);
    moduleClasspath.setComponent(new JComboBox());

    propertiesTableModel = new TestNGParametersTableModel();
    listenerModel = new TestNGListenersTableModel();

    TextFieldWithBrowseButton textFieldWithBrowseButton = new TextFieldWithBrowseButton();
    propertiesFile.setComponent(textFieldWithBrowseButton);

    FileChooserDescriptor propertiesFileDescriptor = new FileChooserDescriptor(true, false, false, false, false, false)
    {
      @Override
      public boolean isFileVisible(VirtualFile virtualFile, boolean showHidden) {
        if (!showHidden && virtualFile.getName().charAt(0) == '.') return false;
        return virtualFile.isDirectory() || "properties".equals(virtualFile.getExtension());
      }
    };

    textFieldWithBrowseButton
        .addBrowseFolderListener("TestNG", "Select .properties file for test properties", project, propertiesFileDescriptor);

    propertiesTableView.setModel(propertiesTableModel);
    propertiesTableView.setShowGrid(true);

    listenersTable.setModel(listenerModel);

    myAddButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent e) {
        propertiesTableModel.addParameter();
        int index = propertiesTableModel.getRowCount() - 1;
        propertiesTableView.setRowSelectionInterval(index, index);
      }
    });
    myRemoveButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent e) {
        int idx = propertiesTableView.getSelectedRow() - 1;
        for (int row : propertiesTableView.getSelectedRows()) {
          propertiesTableModel.removeProperty(row);
        }
        if (idx > -1) propertiesTableView.setRowSelectionInterval(idx, idx);
      }
    });
  }

  @Override
  protected void disposeEditor() {
  }

  public void onTypeChanged(TestType type) {
    //LOGGER.info("onTypeChanged with " + type);
    if (type != TestType.PACKAGE && type != TestType.SUITE) {
      moduleClasspath.setEnabled(true);
    } else {
      evaluateModuleClassPath();
    }
    if (type == TestType.PACKAGE) {
      packageTest.setSelected(true);
      packageField.setEnabled(true);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
    } else if (type == TestType.CLASS) {
      classTest.setSelected(true);
      packageField.setEnabled(false);
      classField.setEnabled(true);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
    } else if (type == TestType.METHOD) {
      methodTest.setSelected(true);
      packageField.setEnabled(false);
      classField.setEnabled(true);
      methodField.setEnabled(true);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
    } else if (type == TestType.GROUP) {
      groupTest.setSelected(true);
      groupField.setEnabled(true);
      packageField.setEnabled(false);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      suiteField.setEnabled(false);
    } else if (type == TestType.SUITE) {
      suiteTest.setSelected(true);
      suiteField.setEnabled(true);
      packageField.setEnabled(false);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
    }
  }

  private class AddTestListenerListener implements ActionListener
  {
    private final Logger LOGGER = Logger.getInstance("TestNG Runner");

    public void actionPerformed(ActionEvent event) {
      final String className = selectListenerClass();
      if (className != null) {
        listenerModel.addListener(className);
        LOGGER.info("Adding listener " + className + " to configuration.");
      }
    }

    @Nullable
    protected GlobalSearchScope getSearchScope(Module[] modules) {
      if (modules == null || modules.length == 0) return null;
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(modules[0]);
      for (int i = 1; i < modules.length; i++) {
        scope.uniteWith(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(modules[i]));
      }
      return scope;
    }

    @Nullable
    protected String selectListenerClass() {
      final GlobalSearchScope searchScope = getSearchScope(config.getModules());
      if (searchScope == null) {
        Messages.showErrorDialog(panel, "Module is not selected", "Can't Browse Listeners");
        return null;
      }
      final TestListenerFilter filter = new TestListenerFilter(searchScope, project);

      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser("Choose Listener Class", filter.getScope(), filter, null);
      chooser.showDialog();
      PsiClass psiclass = chooser.getSelectedClass();
      if (psiclass == null) {
        return null;
      } else {
        return JavaExecutionUtil.getRuntimeQualifiedName(psiclass);
      }
    }
  }
}
