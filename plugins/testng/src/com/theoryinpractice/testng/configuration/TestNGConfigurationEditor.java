// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.theoryinpractice.testng.configuration;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.execution.ui.ShortenCommandLineModeCombo;
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
import com.intellij.openapi.ui.LabeledComponentNoThrow;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.table.TableView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.theoryinpractice.testng.MessageInfoException;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.configuration.browser.GroupBrowser;
import com.theoryinpractice.testng.configuration.browser.PackageBrowser;
import com.theoryinpractice.testng.configuration.browser.SuiteBrowser;
import com.theoryinpractice.testng.configuration.browser.TestClassBrowser;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestListenerFilter;
import com.theoryinpractice.testng.model.TestNGConfigurationModel;
import com.theoryinpractice.testng.model.TestNGListenersTableModel;
import com.theoryinpractice.testng.model.TestNGParametersTableModel;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.ResourceBundle;

public class TestNGConfigurationEditor<T extends TestNGConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
  //private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private final Project project;

  private final JPanel panel;

  private final LabeledComponentNoThrow<EditorTextFieldWithBrowseButton> classField;
  private final LabeledComponentNoThrow<ModuleDescriptionsComboBox> moduleClasspath;
  private final JrePathEditor alternateJDK;
  private final ConfigurationModuleSelector moduleSelector;
  private final JComboBox<TestType> myTestKind;
  private final JBLabel myTestLabel;
  private final TestNGConfigurationModel model;
  private final LabeledComponentNoThrow<EditorTextFieldWithBrowseButton> methodField;
  private final LabeledComponentNoThrow<EditorTextFieldWithBrowseButton> packageField;
  private final LabeledComponentNoThrow<TextFieldWithBrowseButton.NoPathCompletion> groupField;
  private final LabeledComponentNoThrow<TextFieldWithBrowseButton> suiteField;
  private JComponent anchor;
  private final JRadioButton packagesInProject;
  private final JRadioButton packagesInModule;
  private final JRadioButton packagesAcrossModules;
  private final JPanel packagePanel;
  private TestNGParametersTableModel propertiesTableModel;
  private final LabeledComponentNoThrow<TextFieldWithBrowseButton> propertiesFile;
  private final LabeledComponentNoThrow<TextFieldWithBrowseButton> outputDirectory;
  private TableView propertiesTableView;
  private final JPanel commonParametersPanel;//temp compilation problems
  private JList myListenersList;
  private final JCheckBox myUseDefaultReportersCheckBox;
  private final LabeledComponentNoThrow<JPanel> myPattern;
  private final JPanel myPropertiesPanel;
  private final JPanel myListenersPanel;
  private final LabeledComponentNoThrow<ShortenCommandLineModeCombo> myShortenCommandLineCombo;
  private final LabeledComponentNoThrow<JCheckBox> myUseModulePath;
  private final LabeledComponentNoThrow<JCheckBox> myAsyncStackTraceForExceptions;
  TextFieldWithBrowseButton myPatternTextField;
  private final CommonJavaParametersPanel commonJavaParameters = new CommonJavaParametersPanel();
  private final ArrayList<Map.Entry<String, String>> propertiesList = new ArrayList<>();
  private TestNGListenersTableModel listenerModel;

  private TestNGConfiguration config;

  public TestNGConfigurationEditor(Project project) {
    this.project = project;
    {
      myShortenCommandLineCombo = new LabeledComponentNoThrow<>();
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      panel = new JPanel();
      panel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(8, 1, new Insets(0, 0, 0, 0), -1, -1));
      panel1.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
      panel.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                            0, false));
      methodField = new LabeledComponentNoThrow();
      methodField.setLabelLocation("West");
      methodField.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.method.label"));
      panel1.add(methodField, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      suiteField = new LabeledComponentNoThrow();
      suiteField.setLabelLocation("West");
      suiteField.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.suite.label"));
      panel1.add(suiteField, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      packagePanel = new JPanel();
      packagePanel.setLayout(new FormLayout("fill:d:noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):grow",
                                            "center:d:noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
      panel1.add(packagePanel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      packageField = new LabeledComponentNoThrow();
      packageField.setLabelLocation("West");
      packageField.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.package.label"));
      CellConstraints cc = new CellConstraints();
      packagePanel.add(packageField, cc.xyw(1, 1, 5));
      packagesInProject = new JRadioButton();
      this.$$$loadButtonText$$$(packagesInProject,
                                this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.in.whole.project.radio"));
      packagePanel.add(packagesInProject, cc.xy(1, 3));
      packagesInModule = new JRadioButton();
      this.$$$loadButtonText$$$(packagesInModule,
                                this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.in.single.module.radio"));
      packagePanel.add(packagesInModule, cc.xy(3, 3));
      packagesAcrossModules = new JRadioButton();
      this.$$$loadButtonText$$$(packagesAcrossModules, this.$$$getMessageFromBundle$$$("messages/TestngBundle",
                                                                                       "testng.configuration.across.module.dependencies.radio"));
      packagePanel.add(packagesAcrossModules, cc.xy(5, 3));
      classField = new LabeledComponentNoThrow();
      classField.setLabelLocation("West");
      classField.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.class.label"));
      panel1.add(classField, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      outputDirectory = new LabeledComponentNoThrow();
      outputDirectory.setLabelLocation("West");
      outputDirectory.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.output.directory"));
      panel1.add(outputDirectory, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      groupField = new LabeledComponentNoThrow();
      groupField.setLabelLocation("West");
      groupField.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.group.label"));
      panel1.add(groupField, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myPattern = new LabeledComponentNoThrow();
      myPattern.setComponentClass("javax.swing.JPanel");
      myPattern.setLabelLocation("West");
      myPattern.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.pattern.label"));
      myPattern.setVisible(true);
      panel1.add(myPattern, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myAsyncStackTraceForExceptions = new LabeledComponentNoThrow();
      myAsyncStackTraceForExceptions.setComponentClass("javax.swing.JCheckBox");
      myAsyncStackTraceForExceptions.setLabelLocation("West");
      myAsyncStackTraceForExceptions.setText("");
      panel1.add(myAsyncStackTraceForExceptions,
                 new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                     false));
      final JBTabbedPane jBTabbedPane1 = new JBTabbedPane();
      panel.add(jBTabbedPane1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(200, 200), null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(7, 1, new Insets(0, 0, 0, 0), -1, -1));
      jBTabbedPane1.addTab(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.jdk.settings.pane"), panel2);
      myShortenCommandLineCombo.setLabelLocation("West");
      myShortenCommandLineCombo.setText(
        this.$$$getMessageFromBundle$$$("messages/ExecutionBundle", "application.configuration.shorten.command.line.label"));
      panel2.add(myShortenCommandLineCombo, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, null, null, 0, false));
      commonParametersPanel = new JPanel();
      commonParametersPanel.setLayout(new BorderLayout(0, 0));
      panel2.add(commonParametersPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      panel2.add(spacer1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      final Spacer spacer2 = new Spacer();
      panel2.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 15), null, 0, false));
      alternateJDK = new JrePathEditor();
      panel2.add(alternateJDK, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
      moduleClasspath = new LabeledComponentNoThrow();
      moduleClasspath.setComponentClass("com.intellij.application.options.ModuleDescriptionsComboBox");
      moduleClasspath.setLabelLocation("West");
      moduleClasspath.setText(
        this.$$$getMessageFromBundle$$$("messages/ExecutionBundle", "application.configuration.use.classpath.and.jdk.of.module.label"));
      panel2.add(moduleClasspath, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
      myUseModulePath = new LabeledComponentNoThrow();
      myUseModulePath.setComponentClass("javax.swing.JCheckBox");
      myUseModulePath.setLabelLocation("West");
      myUseModulePath.setText("");
      panel2.add(myUseModulePath, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
      final JPanel panel3 = new JPanel();
      panel3.setLayout(new BorderLayout(0, 0));
      jBTabbedPane1.addTab(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.parameters.pane"), panel3);
      propertiesFile = new LabeledComponentNoThrow();
      propertiesFile.setLabelLocation("West");
      propertiesFile.setText(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.properties.file"));
      panel3.add(propertiesFile, BorderLayout.NORTH);
      myPropertiesPanel = new JPanel();
      myPropertiesPanel.setLayout(new BorderLayout(0, 0));
      panel3.add(myPropertiesPanel, BorderLayout.CENTER);
      final JPanel panel4 = new JPanel();
      panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
      jBTabbedPane1.addTab(this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.listeners.pane"), panel4);
      myListenersPanel = new JPanel();
      myListenersPanel.setLayout(new BorderLayout(0, 0));
      panel4.add(myListenersPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       new Dimension(-1, 100), null, 0, false));
      myUseDefaultReportersCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(myUseDefaultReportersCheckBox, this.$$$getMessageFromBundle$$$("messages/TestngBundle",
                                                                                               "testng.configuration.use.default.reporters.option"));
      panel4.add(myUseDefaultReportersCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
      final Spacer spacer3 = new Spacer();
      panel.add(spacer3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      final JPanel panel5 = new JPanel();
      panel5.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
      panel.add(panel5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                            0, false));
      final Spacer spacer4 = new Spacer();
      panel5.add(spacer4, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      myTestKind = new JComboBox();
      panel5.add(myTestKind, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
      myTestLabel = new JBLabel();
      this.$$$loadLabelText$$$(myTestLabel,
                               this.$$$getMessageFromBundle$$$("messages/TestngBundle", "testng.configuration.test.kind.label"));
      panel5.add(myTestLabel,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }
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

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return panel; }

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
                                   ReadAction.nonBlocking(() -> FilenameIndex.getFilesByName(project, PsiJavaModule.MODULE_INFO_FILE,
                                                                                             GlobalSearchScope.projectScope(
                                                                                               project)).length > 0)
                                     .expireWith(this)
                                     .finishOnUiThread(ModalityState.stateForComponent(myUseModulePath),
                                                       visible -> myUseModulePath.setVisible(visible))
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

  @Override
  protected @NotNull JComponent createEditor() {
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

    protected @Nullable GlobalSearchScope getSearchScope(Module[] modules) {
      if (modules == null || modules.length == 0) return null;
      GlobalSearchScope[] scopes =
        ContainerUtil.map2Array(modules, GlobalSearchScope.class, GlobalSearchScope::moduleWithDependenciesAndLibrariesScope);
      return GlobalSearchScope.union(scopes);
    }

    protected @Nullable String selectListenerClass() {
      GlobalSearchScope searchScope = getSearchScope(config.getModules());
      if (searchScope == null) {
        searchScope = GlobalSearchScope.allScope(project);
      }
      final TestListenerFilter filter = new TestListenerFilter(searchScope, project);

      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
        .createWithInnerClassesScopeChooser(TestngBundle.message("testng.config.editor.dialog.title.choose.listener.class"),
                                            filter.getScope(), filter, null);
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
