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

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import com.intellij.util.TextFieldCompletionProvider;
import com.theoryinpractice.testng.MessageInfoException;
import com.theoryinpractice.testng.configuration.browser.*;
import com.theoryinpractice.testng.model.*;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Map;

public class TestNGConfigurationEditor extends SettingsEditor<TestNGConfiguration> implements PanelWithAnchor {
  //private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private final Project project;

  private JPanel panel;

  private LabeledComponent<EditorTextFieldWithBrowseButton> classField;
  private LabeledComponent<JComboBox> moduleClasspath;
  private AlternativeJREPanel alternateJDK;
  private final ConfigurationModuleSelector moduleSelector;
  private JRadioButton suiteTest;
  private JRadioButton packageTest;
  private JRadioButton classTest;
  private JRadioButton methodTest;
  private JRadioButton groupTest;
  private JRadioButton patternTest;
  private final TestNGConfigurationModel model;
  private LabeledComponent<EditorTextFieldWithBrowseButton> methodField;
  private LabeledComponent<EditorTextFieldWithBrowseButton> packageField;
  private LabeledComponent<TextFieldWithBrowseButton.NoPathCompletion> groupField;
  private LabeledComponent<TextFieldWithBrowseButton> suiteField;
  private JComponent anchor;
  private JRadioButton packagesInProject;
  private JRadioButton packagesInModule;
  private JRadioButton packagesAcrossModules;
  private JPanel packagePanel;
  private TestNGParametersTableModel propertiesTableModel;
  private LabeledComponent<TextFieldWithBrowseButton> propertiesFile;
  private LabeledComponent<TextFieldWithBrowseButton> outputDirectory;
  private TableView propertiesTableView;
  private JPanel commonParametersPanel;//temp compilation problems
  private JList myListenersList;
  private JCheckBox myUseDefaultReportersCheckBox;
  private LabeledComponent<JPanel> myPattern;
  private JPanel myPropertiesPanel;
  private JPanel myListenersPanel;
  TextFieldWithBrowseButton myPatternTextField;
  private final CommonJavaParametersPanel commonJavaParameters = new CommonJavaParametersPanel();
  private final ArrayList<Map.Entry> propertiesList = new ArrayList<Map.Entry>();
  private TestNGListenersTableModel listenerModel;

  private TestNGConfiguration config;

  public TestNGConfigurationEditor(Project project) {
    this.project = project;
    BrowseModuleValueActionListener[] browseListeners = new BrowseModuleValueActionListener[]{new PackageBrowser(project),
      new TestClassBrowser(project, this), new MethodBrowser(project, this), new GroupBrowser(project, this), new SuiteBrowser(project),
      new TestClassBrowser(project, this) {
        @Override
        protected void onClassChoosen(PsiClass psiClass) {
          final JTextField textField = myPatternTextField.getTextField();
          final String text = textField.getText();
          textField.setText(text + (text.length() > 0 ? "||" : "") + psiClass.getQualifiedName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
          showDialog();
        }
      }};
    model = new TestNGConfigurationModel(project);
    model.setListener(this);
    createView();
    moduleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    commonJavaParameters.setModuleContext(moduleSelector.getModule());
    moduleClasspath.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        commonJavaParameters.setModuleContext(moduleSelector.getModule());
      }
    });

    final JPanel panel = myPattern.getComponent();
    panel.setLayout(new BorderLayout());
    myPatternTextField = new TextFieldWithBrowseButton();
    myPatternTextField.setButtonIcon(IconUtil.getAddIcon());
    panel.add(myPatternTextField, BorderLayout.CENTER);
    final FixedSizeButton editBtn = new FixedSizeButton();
    editBtn.setIcon(AllIcons.Actions.ShowViewer);
    editBtn.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(myPatternTextField.getTextField(), "Configure suite tests", "EditParametersPopupWindow");
      }
    });
    panel.add(editBtn, BorderLayout.EAST);

    registerListener(new JRadioButton[]{packageTest, classTest, methodTest, groupTest, suiteTest, patternTest}, new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ButtonModel buttonModel = (ButtonModel)e.getSource();
        if (buttonModel.isSelected()) {
          if (buttonModel == packageTest.getModel()) {
            model.setType(TestType.PACKAGE);
          }
          else if (buttonModel == classTest.getModel()) {
            model.setType(TestType.CLASS);
          }
          else if (buttonModel == methodTest.getModel()) {
            model.setType(TestType.METHOD);
          }
          else if (buttonModel == groupTest.getModel()) {
            model.setType(TestType.GROUP);
          }
          else if (buttonModel == suiteTest.getModel()) {
            model.setType(TestType.SUITE);
          }
          else if (buttonModel == patternTest.getModel()) {
            model.setType(TestType.PATTERN);
          }
        }
      }
    });
    registerListener(new JRadioButton[]{packagesInProject, packagesInModule, packagesAcrossModules}, null);
    packagesInProject.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        evaluateModuleClassPath();
      }
    });

    LabeledComponent[] components = new LabeledComponent[]{packageField, classField, methodField, groupField, suiteField, myPattern};
    for (int i = 0; i < components.length; i++) {
      JComponent field = components[i].getComponent();
      Object document = model.getDocument(i);
      if (field instanceof TextFieldWithBrowseButton) {
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((PlainDocument)document);
      }
      else if (field instanceof EditorTextFieldWithBrowseButton) {
        final com.intellij.openapi.editor.Document componentDocument =
          ((EditorTextFieldWithBrowseButton)field).getChildComponent().getDocument();
        
        model.setDocument(i, componentDocument);
      }
      else {
        field = myPatternTextField;
        document = new PlainDocument();
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
        model.setDocument(i, document);
      }

      browseListeners[i].setField((ComponentWithBrowseButton)field);
    }
    model.setType(TestType.CLASS);
    propertiesFile.getComponent().getTextField().setDocument(model.getPropertiesFileDocument());
    outputDirectory.getComponent().getTextField().setDocument(model.getOutputDirectoryDocument());

    commonJavaParameters.setProgramParametersLabel(ExecutionBundle.message("junit.configuration.test.runner.parameters.label"));

    setAnchor(outputDirectory.getLabel());
    alternateJDK.setAnchor(moduleClasspath.getLabel());
    commonJavaParameters.setAnchor(moduleClasspath.getLabel());
  }

  private void evaluateModuleClassPath() {
    final boolean allPackagesInProject = packagesInProject.isSelected() && packagePanel.isVisible();
    moduleClasspath.setEnabled(!allPackagesInProject);
    if (allPackagesInProject) {
      moduleClasspath.getComponent().setSelectedItem(null);
    }
  }

  private void redisplay() {
    if (packageTest.isSelected()) {
      packagePanel.setVisible(true);
      packageField.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (classTest.isSelected()) {
      packagePanel.setVisible(false);
      classField.setVisible(true);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (methodTest.isSelected()) {
      packagePanel.setVisible(false);
      classField.setVisible(true);
      methodField.setVisible(true);
      groupField.setVisible(false);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (groupTest.isSelected()) {
      packagePanel.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(true);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (suiteTest.isSelected()) {
      packagePanel.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(true);
      myPattern.setVisible(false);
    }
    else if (patternTest.isSelected()) {
      packagePanel.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(false);
      myPattern.setVisible(true);
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
    }
    else if (scope == TestSearchScope.MODULE_WITH_DEPENDENCIES) {
      packagesAcrossModules.setSelected(true);
    }
    else {
      packagesInProject.setSelected(true);
    }
    evaluateModuleClassPath();
    alternateJDK.init(config.ALTERNATIVE_JRE_PATH, config.ALTERNATIVE_JRE_PATH_ENABLED);
    propertiesList.clear();
    propertiesList.addAll(data.TEST_PROPERTIES.entrySet());
    propertiesTableModel.setParameterList(propertiesList);

    listenerModel.setListenerList(data.TEST_LISTENERS);
    myUseDefaultReportersCheckBox.setSelected(data.USE_DEFAULT_REPORTERS);
  }

  @Override
  public void applyEditorTo(TestNGConfiguration config) {
    model.apply(getModuleSelector().getModule(), config);
    getModuleSelector().applyTo(config);
    TestData data = config.getPersistantData();
    if (!classTest.isSelected() && !methodTest.isSelected()) {
      if (packagesInProject.isSelected()) {
        data.setScope(TestSearchScope.WHOLE_PROJECT);
      }
      else if (packagesInModule.isSelected()) {
        data.setScope(TestSearchScope.SINGLE_MODULE);
      }
      else if (packagesAcrossModules.isSelected()) data.setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
    }
    else {
      data.setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
    }
    commonJavaParameters.applyTo(config);
    config.ALTERNATIVE_JRE_PATH = alternateJDK.getPath();
    config.ALTERNATIVE_JRE_PATH_ENABLED = alternateJDK.isPathEnabled();

    data.TEST_PROPERTIES.clear();
    for (Map.Entry<String, String> entry : propertiesList) {
      data.TEST_PROPERTIES.put(entry.getKey(), entry.getValue());
    }

    data.TEST_LISTENERS.clear();
    data.TEST_LISTENERS.addAll(listenerModel.getListenerList());

    data.USE_DEFAULT_REPORTERS = myUseDefaultReportersCheckBox.isSelected();
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return moduleSelector;
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return panel;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    methodField.setAnchor(anchor);
    packageField.setAnchor(anchor);
    groupField.setAnchor(anchor);
    suiteField.setAnchor(anchor);
    outputDirectory.setAnchor(anchor);
    classField.setAnchor(anchor);
    myPattern.setAnchor(anchor);
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
    patternTest.setSelected(false);
    patternTest.setEnabled(true);

    classField.setComponent(new EditorTextFieldWithBrowseButton(project, true, new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        try {
          if (declaration instanceof PsiClass &&
              new TestClassBrowser(project, TestNGConfigurationEditor.this).getFilter().isAccepted((PsiClass)declaration)) {
            return Visibility.VISIBLE;
          }
        }
        catch (MessageInfoException e) {
          return Visibility.NOT_VISIBLE;
        }
        return Visibility.NOT_VISIBLE;
      }
    }));

    final EditorTextFieldWithBrowseButton methodEditorTextField = new EditorTextFieldWithBrowseButton(project, true, 
                                                                                                      JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE, 
                                                                                                      PlainTextLanguage.INSTANCE.getAssociatedFileType());
    new TextFieldCompletionProvider() {
      @Override
      protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
        final String className = getClassName();
        if (className.trim().length() == 0) {
          return;
        }
        final PsiClass testClass = getModuleSelector().findClass(className);
        if (testClass == null) return;
        for (PsiMethod psiMethod : testClass.getAllMethods()) {
          if (TestNGUtil.hasTest(psiMethod)) {
            result.addElement(LookupElementBuilder.create(psiMethod.getName()));
          }
        }
      }
    }.apply(methodEditorTextField.getChildComponent());
    methodField.setComponent(methodEditorTextField);

    groupField.setComponent(new TextFieldWithBrowseButton.NoPathCompletion());
    suiteField.setComponent(new TextFieldWithBrowseButton());
    packageField.setVisible(true);
    packageField.setEnabled(true);
    packageField.setComponent(new EditorTextFieldWithBrowseButton(project, false));


    TextFieldWithBrowseButton outputDirectoryButton = new TextFieldWithBrowseButton();
    outputDirectory.setComponent(outputDirectoryButton);
    outputDirectoryButton.addBrowseFolderListener("TestNG", "Select test output directory", project,
                                                  FileChooserDescriptorFactory.createSingleFolderDescriptor());
    moduleClasspath.setEnabled(true);
    moduleClasspath.setComponent(new JComboBox());

    propertiesTableModel = new TestNGParametersTableModel();
    listenerModel = new TestNGListenersTableModel();

    TextFieldWithBrowseButton textFieldWithBrowseButton = new TextFieldWithBrowseButton();
    propertiesFile.setComponent(textFieldWithBrowseButton);

    FileChooserDescriptor propertiesFileDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile virtualFile, boolean showHidden) {
        if (!showHidden && virtualFile.getName().charAt(0) == '.') return false;
        return virtualFile.isDirectory() || "properties".equals(virtualFile.getExtension());
      }
    };

    textFieldWithBrowseButton
      .addBrowseFolderListener("TestNG", "Select .properties file for test properties", project, propertiesFileDescriptor);

    propertiesTableView = new TableView();
    propertiesTableView.setModelAndUpdateColumns(propertiesTableModel);
    propertiesTableView.setShowGrid(true);

    myPropertiesPanel.add(
      ToolbarDecorator.createDecorator(propertiesTableView)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            propertiesTableModel.addParameter();
            int index = propertiesTableModel.getRowCount() - 1;
            propertiesTableView.setRowSelectionInterval(index, index);
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int idx = propertiesTableView.getSelectedRow() - 1;
          for (int row : propertiesTableView.getSelectedRows()) {
            propertiesTableModel.removeProperty(row);
          }
          if (idx > -1) propertiesTableView.setRowSelectionInterval(idx, idx);
        }
      }).disableUpDownActions().createPanel(), BorderLayout.CENTER);

    myListenersList = new JBList(listenerModel);
    myListenersPanel.add(
      ToolbarDecorator.createDecorator(myListenersList).setAddAction(new AddActionButtonRunnable())
        .setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            int idx = myListenersList.getSelectedIndex() - 1;
            for (int row : myListenersList.getSelectedIndices()) {
              listenerModel.removeListener(row);
            }
            if (idx > -1) myListenersList.setSelectedIndex(idx);
          }
        }).setAddActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return !project.isDefault();
        }
      }).disableUpDownActions().createPanel(), BorderLayout.CENTER);
  }

  public void onTypeChanged(TestType type) {
    //LOGGER.info("onTypeChanged with " + type);
    if (type == TestType.PACKAGE) {
      packageTest.setSelected(true);
      packageField.setEnabled(true);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.CLASS) {
      classTest.setSelected(true);
      packageField.setEnabled(false);
      classField.setEnabled(true);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.METHOD) {
      methodTest.setSelected(true);
      packageField.setEnabled(false);
      classField.setEnabled(true);
      methodField.setEnabled(true);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.GROUP) {
      groupTest.setSelected(true);
      groupField.setEnabled(true);
      packageField.setVisible(false);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.SUITE) {
      suiteTest.setSelected(true);
      suiteField.setEnabled(true);
      packageField.setVisible(false);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.PATTERN) {
      patternTest.setSelected(true);
      myPattern.setEnabled(true);
      suiteField.setEnabled(false);
      packageField.setVisible(false);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
    }
    redisplay();
    evaluateModuleClassPath();
  }

  private class AddActionButtonRunnable implements AnActionButtonRunnable {
    private final Logger LOGGER = Logger.getInstance("TestNG Runner");

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
      GlobalSearchScope searchScope = getSearchScope(config.getModules());
      if (searchScope == null) {
        searchScope = GlobalSearchScope.allScope(project);
      }
      final TestListenerFilter filter = new TestListenerFilter(searchScope, project);

      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
        .createWithInnerClassesScopeChooser("Choose Listener Class", filter.getScope(), filter, null);
      chooser.showDialog();
      PsiClass psiclass = chooser.getSelected();
      if (psiclass == null) {
        return null;
      }
      else {
        return JavaExecutionUtil.getRuntimeQualifiedName(psiclass);
      }
    }

    @Override
    public void run(AnActionButton button) {
      final String className = selectListenerClass();
      if (className != null) {
        listenerModel.addListener(className);
        LOGGER.info("Adding listener " + className + " to configuration.");
      }
    }
  }
}
