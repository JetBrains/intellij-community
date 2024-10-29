// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.theoryinpractice.testng.configuration;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.*;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.MessageInfoException;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.configuration.browser.GroupBrowser;
import com.theoryinpractice.testng.configuration.browser.PackageBrowser;
import com.theoryinpractice.testng.configuration.browser.SuiteBrowser;
import com.theoryinpractice.testng.configuration.browser.TestClassBrowser;
import com.theoryinpractice.testng.model.*;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Map;

public class TestNGConfigurationEditor<T extends TestNGConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
  //private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private final Project project;

  private JPanel panel;

  private LabeledComponent<EditorTextFieldWithBrowseButton> classField;
  private LabeledComponent<ModuleDescriptionsComboBox> moduleClasspath;
  private JrePathEditor alternateJDK;
  private final ConfigurationModuleSelector moduleSelector;
  private JComboBox<TestType> myTestKind;
  private JBLabel myTestLabel;
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
  private LabeledComponent<ShortenCommandLineModeCombo> myShortenCommandLineCombo;
  private LabeledComponent<JCheckBox> myUseModulePath;
  private LabeledComponent<JCheckBox> myAsyncStackTraceForExceptions;
  TextFieldWithBrowseButton myPatternTextField;
  private final CommonJavaParametersPanel commonJavaParameters = new CommonJavaParametersPanel();
  private final ArrayList<Map.Entry<String, String>> propertiesList = new ArrayList<>();
  private TestNGListenersTableModel listenerModel;

  private TestNGConfiguration config;

  public TestNGConfigurationEditor(Project project) {
    this.project = project;
    BrowseModuleValueActionListener[] browseListeners = new BrowseModuleValueActionListener[]{new PackageBrowser(project),
      new TestClassBrowser(project, this), new TestNGMethodBrowser(project), new GroupBrowser(project, this), new SuiteBrowser(project),
      new TestClassBrowser(project, this) {
        @Override
        protected void onClassChoosen(PsiClass psiClass) {
          final JTextField textField = myPatternTextField.getTextField();
          final String text = textField.getText();
          textField.setText(text + (!text.isEmpty() ? "||" : "") + psiClass.getQualifiedName());
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
    alternateJDK.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(getModulesComponent(), false));
    commonJavaParameters.setModuleContext(moduleSelector.getModule());
    moduleClasspath.getComponent().addActionListener(e -> commonJavaParameters.setModuleContext(moduleSelector.getModule()));
    commonJavaParameters.setHasModuleMacro();

    final JPanel panel = myPattern.getComponent();
    panel.setLayout(new BorderLayout());
    myPatternTextField = new TextFieldWithBrowseButton(new ExpandableTextField());
    myPatternTextField.setButtonIcon(IconUtil.getAddIcon());
    panel.add(myPatternTextField, BorderLayout.CENTER);

    final CollectionComboBoxModel<TestType> testKindModel = new CollectionComboBoxModel<>();
    for (TestType type : TestType.values()) {
      if (type != TestType.SOURCE || Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY)) {
        testKindModel.add(type);
      }
    }
    myTestKind.setModel(testKindModel);
    myTestKind.addActionListener(e -> this.model.setType((TestType)myTestKind.getSelectedItem()));
    myTestKind.setRenderer(SimpleListCellRenderer.create("", value -> value.getPresentableName()));
    registerListener(new JRadioButton[]{packagesInProject, packagesInModule, packagesAcrossModules}, null);
    packagesInProject.addChangeListener(e -> evaluateModuleClassPath());

    LabeledComponent[] components = new LabeledComponent[]{packageField, classField, methodField, groupField, suiteField, myPattern};
    for (int i = 0; i < components.length; i++) {
      JComponent field = components[i].getComponent();
      Object document = model.getDocument(i);
      if (field instanceof TextFieldWithBrowseButton) {
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((PlainDocument)document);
      }
      else if (field instanceof EditorTextFieldWithBrowseButton) {
        document = ((EditorTextFieldWithBrowseButton)field).getChildComponent().getDocument();
      }
      else {
        field = myPatternTextField;
        document = new PlainDocument();
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
      }

      browseListeners[i].setField((ComponentWithBrowseButton)field);
      if (browseListeners[i] instanceof MethodBrowser) {
        final EditorTextField childComponent = (EditorTextField)((ComponentWithBrowseButton<?>)field).getChildComponent();
        ((MethodBrowser)browseListeners[i]).installCompletion(childComponent);
        document = childComponent.getDocument();
      }
      model.setDocument(i, document);
    }
    model.setType(TestType.CLASS);
    propertiesFile.getComponent().getTextField().setDocument(model.getPropertiesFileDocument());
    outputDirectory.getComponent().getTextField().setDocument(model.getOutputDirectoryDocument());

    commonJavaParameters.setProgramParametersLabel(TestngBundle.message("junit.configuration.test.runner.parameters.label"));

    myShortenCommandLineCombo.setComponent(new ShortenCommandLineModeCombo(project, alternateJDK, getModulesComponent()) {
      @Override
      protected boolean productionOnly() {
        return false;
      }
    });
    setAnchor(outputDirectory.getLabel());
    alternateJDK.setAnchor(moduleClasspath.getLabel());
    commonJavaParameters.setAnchor(moduleClasspath.getLabel());
    myShortenCommandLineCombo.setAnchor(moduleClasspath.getLabel());
    myUseModulePath.setAnchor(moduleClasspath.getLabel());
    myUseModulePath.getComponent().setText(ExecutionBundle.message("use.module.path.checkbox.label"));
    myUseModulePath.getComponent().setSelected(true);

    myAsyncStackTraceForExceptions.setAnchor(outputDirectory.getLabel());
    myAsyncStackTraceForExceptions.getComponent().setText(TestngBundle.message("async.stack.trace.for.exceptions.label"));
    myAsyncStackTraceForExceptions.getComponent().setSelected(true);
  }

  private void evaluateModuleClassPath() {
    final boolean allPackagesInProject = packagesInProject.isSelected() && packagePanel.isVisible();
    moduleClasspath.setEnabled(!allPackagesInProject);
    if (allPackagesInProject) {
      moduleClasspath.getComponent().setSelectedItem(null);
    }
  }

  private void redisplay() {
    final TestType testKind = (TestType)myTestKind.getSelectedItem();
    if (testKind == TestType.PACKAGE) {
      packagePanel.setVisible(true);
      packageField.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (testKind == TestType.CLASS) {
      packagePanel.setVisible(false);
      classField.setVisible(true);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (testKind == TestType.METHOD || testKind == TestType.SOURCE) {
      packagePanel.setVisible(false);
      classField.setVisible(true);
      methodField.setVisible(true);
      groupField.setVisible(false);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (testKind == TestType.GROUP) {
      packagePanel.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(true);
      suiteField.setVisible(false);
      myPattern.setVisible(false);
    }
    else if (testKind == TestType.SUITE) {
      packagePanel.setVisible(true);
      classField.setVisible(false);
      methodField.setVisible(false);
      groupField.setVisible(false);
      suiteField.setVisible(true);
      myPattern.setVisible(false);
    }
    else if (testKind == TestType.PATTERN) {
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

  public ModuleDescriptionsComboBox getModulesComponent() {
    return moduleClasspath.getComponent();
  }

  @Override
  protected void resetEditorFrom(@NotNull TestNGConfiguration config) {
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
    alternateJDK.setPathOrName(config.getAlternativeJrePath(), config.ALTERNATIVE_JRE_PATH_ENABLED);
    propertiesList.clear();
    propertiesList.addAll(data.TEST_PROPERTIES.entrySet());
    propertiesTableModel.setParameterList(propertiesList);

    listenerModel.setListenerList(data.TEST_LISTENERS);
    myUseDefaultReportersCheckBox.setSelected(data.USE_DEFAULT_REPORTERS);
    myShortenCommandLineCombo.getComponent().setSelectedItem(config.getShortenCommandLine());
    myUseModulePath.getComponent().setSelected(config.isUseModulePath());
    myAsyncStackTraceForExceptions.getComponent().setSelected(config.isPrintAsyncStackTraceForExceptions());
    if (!project.isDefault()) {
      SwingUtilities.invokeLater(() ->
                                   ReadAction.nonBlocking(() -> FilenameIndex.getFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, GlobalSearchScope.projectScope(project)).length > 0)
                                     .expireWith(this)
                                     .finishOnUiThread(ModalityState.stateForComponent(myUseModulePath), visible -> myUseModulePath.setVisible(visible))
                                     .submit(NonUrgentExecutor.getInstance()));
    }
  }

  @Override
  public void applyEditorTo(@NotNull TestNGConfiguration config) {
    model.apply(getModuleSelector().getModule(), config);
    getModuleSelector().applyTo(config);
    TestData data = config.getPersistantData();
    final TestType testKind = (TestType)myTestKind.getSelectedItem();
    if (testKind != TestType.CLASS && testKind != TestType.METHOD && testKind != TestType.SOURCE) {
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
    config.setAlternativeJrePath(alternateJDK.getJrePathOrName());
    config.ALTERNATIVE_JRE_PATH_ENABLED = alternateJDK.isAlternativeJreSelected();

    data.TEST_PROPERTIES.clear();
    for (Map.Entry<String, String> entry : propertiesList) {
      data.TEST_PROPERTIES.put(entry.getKey(), entry.getValue());
    }

    data.TEST_LISTENERS.clear();
    data.TEST_LISTENERS.addAll(listenerModel.getListenerList());

    data.USE_DEFAULT_REPORTERS = myUseDefaultReportersCheckBox.isSelected();
    config.setShortenCommandLine(myShortenCommandLineCombo.getComponent().getSelectedItem());

    config.setUseModulePath(myUseModulePath.isVisible() && myUseModulePath.getComponent().isSelected());

    config.setPrintAsyncStackTraceForExceptions(myAsyncStackTraceForExceptions.getComponent().isSelected());
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
    myTestLabel.setAnchor(anchor);
  }

  private void createUIComponents() {
    myShortenCommandLineCombo = new LabeledComponent<>();
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
    classField.setComponent(new EditorTextFieldWithBrowseButton(project, true, (declaration, place) -> {
      if (declaration instanceof PsiClass && place.getParent() instanceof PsiJavaCodeReferenceElement) {
        return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
      }
      try {
        if (declaration instanceof PsiClass &&
            new TestClassBrowser(project, this).getFilter().isAccepted((PsiClass)declaration)) {
          return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
        }
      }
      catch (MessageInfoException e) {
        return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
      }
      return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
    }));

    final EditorTextFieldWithBrowseButton methodEditorTextField = new EditorTextFieldWithBrowseButton(project, true,
                                                                                                      JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE,
                                                                                                      PlainTextLanguage.INSTANCE.getAssociatedFileType());
    methodField.setComponent(methodEditorTextField);

    groupField.setComponent(new TextFieldWithBrowseButton.NoPathCompletion());
    suiteField.setComponent(new TextFieldWithBrowseButton());
    packageField.setVisible(true);
    packageField.setEnabled(true);
    packageField.setComponent(new EditorTextFieldWithBrowseButton(project, false));


    TextFieldWithBrowseButton outputDirectoryButton = new TextFieldWithBrowseButton();
    outputDirectory.setComponent(outputDirectoryButton);
    outputDirectoryButton.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(TestngBundle.message("testng.output.directory.button.title"))
      .withDescription(TestngBundle.message("testng.select.output.directory")));
    moduleClasspath.setEnabled(true);

    propertiesTableModel = new TestNGParametersTableModel();
    listenerModel = new TestNGListenersTableModel();

    TextFieldWithBrowseButton textFieldWithBrowseButton = new TextFieldWithBrowseButton();
    propertiesFile.setComponent(textFieldWithBrowseButton);

    textFieldWithBrowseButton.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor("properties")
      .withTitle(TestngBundle.message("testng.browse.button.title"))
      .withDescription(TestngBundle.message("testng.select.properties.file")));

    propertiesTableView = new TableView(propertiesTableModel);

    myPropertiesPanel.add(
      ToolbarDecorator.createDecorator(propertiesTableView)
        .setAddAction(button -> {
          propertiesTableModel.addParameter();
          int index = propertiesTableModel.getRowCount() - 1;
          propertiesTableView.setRowSelectionInterval(index, index);
        }).setRemoveAction(button -> {
          int idx = propertiesTableView.getSelectedRow() - 1;
          for (int row : propertiesTableView.getSelectedRows()) {
            propertiesTableModel.removeProperty(row);
          }
          if (idx > -1) propertiesTableView.setRowSelectionInterval(idx, idx);
        }).disableUpDownActions().createPanel(), BorderLayout.CENTER);

    myListenersList = new JBList(listenerModel);
    myListenersPanel.add(
      ToolbarDecorator.createDecorator(myListenersList).setAddAction(new AddActionButtonRunnable())
        .setRemoveAction(button -> {
          int idx = myListenersList.getSelectedIndex() - 1;
          for (int row : myListenersList.getSelectedIndices()) {
            listenerModel.removeListener(row);
          }
          if (idx > -1) myListenersList.setSelectedIndex(idx);
        }).setAddActionUpdater(e -> !project.isDefault()).disableUpDownActions().createPanel(), BorderLayout.CENTER);
  }

  public void onTypeChanged(TestType type) {
    //LOGGER.info("onTypeChanged with " + type);
    myTestKind.setSelectedItem(type);
    if (type == TestType.PACKAGE) {
      packageField.setEnabled(true);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.CLASS) {
      packageField.setEnabled(false);
      classField.setEnabled(true);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.METHOD || type == TestType.SOURCE) {
      packageField.setEnabled(false);
      classField.setEnabled(true);
      methodField.setEnabled(true);
      groupField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.GROUP) {
      groupField.setEnabled(true);
      packageField.setVisible(false);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      suiteField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.SUITE) {
      suiteField.setEnabled(true);
      packageField.setVisible(false);
      classField.setEnabled(false);
      methodField.setEnabled(false);
      groupField.setEnabled(false);
      myPattern.setEnabled(false);
    }
    else if (type == TestType.PATTERN) {
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
      GlobalSearchScope[] scopes =
        ContainerUtil.map2Array(modules, GlobalSearchScope.class, GlobalSearchScope::moduleWithDependenciesAndLibrariesScope);
      return GlobalSearchScope.union(scopes);
    }

    @Nullable
    protected String selectListenerClass() {
      GlobalSearchScope searchScope = getSearchScope(config.getModules());
      if (searchScope == null) {
        searchScope = GlobalSearchScope.allScope(project);
      }
      final TestListenerFilter filter = new TestListenerFilter(searchScope, project);

      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
        .createWithInnerClassesScopeChooser(TestngBundle.message("testng.config.editor.dialog.title.choose.listener.class"), filter.getScope(), filter, null);
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

  private class TestNGMethodBrowser extends MethodBrowser {
    TestNGMethodBrowser(Project project) {
      super(project);
    }

    @Override
    protected Condition<PsiMethod> getFilter(PsiClass testClass) {
      return TestNGUtil::hasTest;
    }

    @Override
    protected String getClassName() {
      return TestNGConfigurationEditor.this.getClassName();
    }

    @Override
    protected ConfigurationModuleSelector getModuleSelector() {
      return moduleSelector;
    }
  }
}
