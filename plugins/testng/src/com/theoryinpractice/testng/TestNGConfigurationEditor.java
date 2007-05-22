/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 3, 2005
 * Time: 6:15:22 PM
 */
package com.theoryinpractice.testng;

import com.intellij.execution.junit2.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit2.configuration.CommonJavaParameters;
import com.intellij.execution.junit2.configuration.ConfigurationModuleSelector;
import com.intellij.execution.junit2.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.table.TableView;
import com.intellij.uiDesigner.core.GridConstraints;
import static com.intellij.uiDesigner.core.GridConstraints.*;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.core.SupportCode;
import com.theoryinpractice.testng.model.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Map;

public class TestNGConfigurationEditor extends SettingsEditor<TestNGConfiguration>
{
    //private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
    private Project project;

    private JPanel panel;

    private LabeledComponent<TextFieldWithBrowseButton> classField;
    private CommonJavaParameters commonParameters;
    private EnvironmentVariablesComponent envVariablesComponent;
    private LabeledComponent<JComboBox> moduleClasspath;
    private AlternativeJREPanel alternateJDK;
    private ConfigurationModuleSelector moduleSelector;
    private JRadioButton suiteTest;
    private JRadioButton packageTest;
    private JRadioButton classTest;
    private JRadioButton methodTest;
    private JRadioButton groupTest;
    private TestNGConfigurationModel model;
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
    private TableView propertiesTableView;
    private ArrayList<Map.Entry> propertiesList;

    public TestNGConfigurationEditor(Project project) {
        this.project = project;
        BrowseModuleValueActionListener[] browseListeners = new BrowseModuleValueActionListener[]
                {
                        new PackageBrowser(project),
                        new TestClassBrowser(project, this),
                        new MethodBrowser(project, this),
                        new GroupBrowser(project, this),
                        new SuiteBrowser(project)
                };
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
            javax.swing.text.Document document = model.getDocument(i);
            field.getTextField().setDocument(document);
            browseListeners[i].setField(field);
        }
        model.setType(TestType.CLASS);
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
        model.reset(config);
        commonParameters.reset(config);
        getModuleSelector().reset(config);
        TestData data = config.getPersistantData();
        TestSearchScope scope = data.getScope();
        if (scope == TestSearchScope.SINGLE_MODULE)
            packagesInModule.setSelected(true);
        else if (scope == TestSearchScope.MODULE_WITH_DEPENDENCIES)
            packagesAcrossModules.setSelected(true);
        else
            packagesInProject.setSelected(true);
        alternateJDK.init(config.ALTERNATIVE_JRE_PATH, config.ALTERNATIVE_JRE_PATH_ENABLED);
        envVariablesComponent.setEnvs(config.getPersistantData().ENV_VARIABLES != null ? FileUtil.toSystemDependentName(config.getPersistantData().ENV_VARIABLES) : "");
        propertiesList = new ArrayList<Map.Entry>();
        propertiesList.addAll(data.TEST_PROPERTIES.entrySet());
        propertiesTableModel.setParameterList(propertiesList);

        propertiesFile.getComponent().getTextField().setDocument(model.getPropertiesFileDocument());
        outputDirectory.getComponent().getTextField().setDocument(model.getOutputDirectoryDocument());
    }

    @Override
    protected void applyEditorTo(TestNGConfiguration config) {
        model.apply(getModuleSelector().getModule(), config);
        getModuleSelector().applyTo(config);
        TestData data = config.getPersistantData();
        if(packageTest.isSelected()) {
            if (packagesInProject.isSelected())
                data.setScope(TestSearchScope.WHOLE_PROJECT);
            else if (packagesInModule.isSelected())
                data.setScope(TestSearchScope.SINGLE_MODULE);
            else if (packagesAcrossModules.isSelected())
                data.setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
        }
        else {
            data.setScope(TestSearchScope.SINGLE_MODULE);
        }
        commonParameters.applyTo(config);
        config.ALTERNATIVE_JRE_PATH = alternateJDK.getPath();
        config.ALTERNATIVE_JRE_PATH_ENABLED = alternateJDK.isPathEnabled();

        data.TEST_PROPERTIES.clear();
        for (Map.Entry<String, String> entry : propertiesList) {
            data.TEST_PROPERTIES.put(entry.getKey(), entry.getValue());
        }

        data.ENV_VARIABLES = envVariablesComponent.getEnvs().trim().length() > 0 ? FileUtil.toSystemIndependentName(envVariablesComponent.getEnvs()) : null;
    }

    public ConfigurationModuleSelector getModuleSelector() {
        return moduleSelector;
    }

    @Override
    protected JComponent createEditor() {
        return panel;
    }

    private void registerListener(JRadioButton buttons[], ChangeListener changelistener) {
        ButtonGroup buttongroup = new ButtonGroup();
        for (JRadioButton button : buttons) {
            button.getModel().addChangeListener(changelistener);
            buttongroup.add(button);
        }

        if (buttongroup.getSelection() == null)
            buttongroup.setSelected(buttons[0].getModel(), true);
    }

    private void createView() {
        panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setLayout(new GridLayoutManager(7, 1, new Insets(0, 0, 0, 0), -1, 5, false, false));
        JPanel header = new JPanel();
        header.setLayout(new GridLayoutManager(1, 6, new Insets(0, 0, 0, 0), -1, -1, false, false));
        panel.add(header, new GridConstraints(0, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, 3, null, null, null));
        packageTest = new JRadioButton();
        packageTest.setText("All in Package");
        packageTest.setMnemonic('P');
        packageTest.setSelected(false);
        SupportCode.setDisplayedMnemonicIndex(packageTest, 7);
        packageTest.setActionCommand("All Tests in Package");
        header.add(packageTest, new GridConstraints(0, 1, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));

        suiteTest = new JRadioButton();
        suiteTest.setText("Suite");
        suiteTest.setMnemonic('S');
        SupportCode.setDisplayedMnemonicIndex(suiteTest, 1);
        suiteTest.setSelected(false);
        suiteTest.setActionCommand("Suite");
        suiteTest.setEnabled(true);
        header.add(suiteTest, new GridConstraints(0, 2, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));

        groupTest = new JRadioButton();
        groupTest.setText("Group");
        groupTest.setMnemonic('G');
        SupportCode.setDisplayedMnemonicIndex(groupTest, 1);
        groupTest.setSelected(false);
        groupTest.setActionCommand("Test Group");
        groupTest.setEnabled(true);
        header.add(groupTest, new GridConstraints(0, 3, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));

        classTest = new JRadioButton();
        classTest.setText("Class");
        classTest.setMnemonic('L');
        SupportCode.setDisplayedMnemonicIndex(classTest, 1);
        classTest.setSelected(false);
        classTest.setActionCommand("Test Class");
        classTest.setEnabled(true);
        header.add(classTest, new GridConstraints(0, 4, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));
        methodTest = new JRadioButton();
        methodTest.setText("Method");
        methodTest.setMnemonic('T');
        SupportCode.setDisplayedMnemonicIndex(methodTest, 2);
        methodTest.setActionCommand("Test Method");
        header.add(methodTest, new GridConstraints(0, 5, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));
        JLabel testLabel = new JLabel();
        testLabel.setText("Test:  ");
        testLabel.setIconTextGap(4);
        testLabel.setHorizontalAlignment(2);
        testLabel.setHorizontalTextPosition(2);
        header.add(testLabel, new GridConstraints(0, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED, null, null, null));

        JPanel top = new JPanel();
        top.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1, false, false));
        panel.add(top, new GridConstraints(1, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, null, null, null));
        JPanel bottom = new JPanel();
        bottom.setLayout(new GridLayoutManager(4, 1, new Insets(5, 5, 5, 5), -1, -1, false, false));
        top.add(bottom, new GridConstraints(0, 0, 1, 1, ANCHOR_NORTH, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, new Dimension(-1, 120), null, null));
        bottom.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Test"));

        classField = new LabeledComponent<TextFieldWithBrowseButton>();
        classField.setComponent(new TextFieldWithBrowseButton());
        classField.setText("&Class:");
        bottom.add(classField, new GridConstraints(1, 0, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));

        methodField = new LabeledComponent<TextFieldWithBrowseButton>();
        methodField.setText("M&ethod:");
        methodField.setComponent(new TextFieldWithBrowseButton());
        bottom.add(methodField, new GridConstraints(2, 0, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));

        groupField = new LabeledComponent<TextFieldWithBrowseButton>();
        groupField.setText("&Group:");
        groupField.setComponent(new TextFieldWithBrowseButton());
        bottom.add(groupField, new GridConstraints(1, 0, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));

        suiteField = new LabeledComponent<TextFieldWithBrowseButton>();
        suiteField.setText("S&uite:");
        suiteField.setComponent(new TextFieldWithBrowseButton());
        bottom.add(suiteField, new GridConstraints(1, 0, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED, null, null, null));

        packagePanel = new JPanel();
        packagePanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1, false, false));
        packageField = new LabeledComponent<TextFieldWithBrowseButton>();
        packageField.setText("Packa&ge:");
        packageField.setVisible(true);
        packageField.setEnabled(true);
        packageField.setComponent(new TextFieldWithBrowseButton());
        packagePanel.add(packageField, new GridConstraints(0, 0, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));
        bottom.add(packagePanel, new GridConstraints(0, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, null, null, null));

        JPanel jpanel5 = new JPanel();
        jpanel5.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1, false, false));
        packagePanel.add(jpanel5, new GridConstraints(1, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, null, null, null));
        jpanel5.add(new JLabel("Search for tests:"), new GridConstraints(0, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED, null, null, null));
        packagesInProject = new JRadioButton();
        packagesInProject.setText("In whole project");
        packagesInProject.setMnemonic('J');
        SupportCode.setDisplayedMnemonicIndex(packagesInProject, 12);
        jpanel5.add(packagesInProject, new GridConstraints(0, 1, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));
        packagesInModule = new JRadioButton();
        packagesInModule.setText("In single module");
        packagesInModule.setMnemonic('S');
        SupportCode.setDisplayedMnemonicIndex(packagesInModule, 3);
        jpanel5.add(packagesInModule, new GridConstraints(0, 2, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));
        packagesAcrossModules = new JRadioButton();
        packagesAcrossModules.setText("Across module dependencies");
        jpanel5.add(packagesAcrossModules, new GridConstraints(0, 3, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));

        bottom.add(new Spacer(), new GridConstraints(3, 0, 1, 1, ANCHOR_CENTER, FILL_VERTICAL, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_GROW, null, null, null));

        panel.add(new Spacer(), new GridConstraints(3, 0, 1, 1, ANCHOR_CENTER, FILL_VERTICAL, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_GROW, null, null, null));

        TabbedPaneWrapper propertyTabs = new TabbedPaneWrapper();

        JPanel customJDKPanel = new JPanel();
        customJDKPanel.setLayout(new GridLayoutManager(6, 1, new Insets(2, 2, 2, 2), -1, -1, false, false));
        commonParameters = new CommonJavaParameters();
        customJDKPanel.add(commonParameters, new GridConstraints(0, 0, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));
        envVariablesComponent = new EnvironmentVariablesComponent();
        customJDKPanel.add(envVariablesComponent, new GridConstraints(1, 0, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));
        moduleClasspath = new LabeledComponent<JComboBox>();
        moduleClasspath.setText("Use classpath and JDK of m&odule:");
        moduleClasspath.setEnabled(true);
        moduleClasspath.setComponent(new JComboBox());
        moduleClasspath.setLabelInsets(new Insets(2, 0, 2, 0));
        JPanel alternateJDKPanelHolder = new JPanel();
        customJDKPanel.add(alternateJDKPanelHolder, new GridConstraints(2, 0, 1, 1, ANCHOR_NORTH, FILL_BOTH, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK, null, null, null));
        customJDKPanel.add(moduleClasspath, new GridConstraints(3, 0, 1, 1, ANCHOR_NORTH, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK, null, null, null));
        alternateJDK = new AlternativeJREPanel();
        customJDKPanel.add(alternateJDK, new GridConstraints(4, 0, 1, 1, ANCHOR_NORTH, FILL_HORIZONTAL, FILL_BOTH, SIZEPOLICY_CAN_SHRINK, null, null, null));
        customJDKPanel.add(new Spacer(), new GridConstraints(5, 0, 1, 1, ANCHOR_NORTH, FILL_VERTICAL, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_GROW, null, null, null));

        LabeledComponent<JPanel> propertiesScrollPane = new LabeledComponent<JPanel>();
        propertiesScrollPane.setText("Set test &parameters:");
        propertiesScrollPane.setEnabled(true);
        propertiesTableModel = new TestNGParametersTableModel();

        JPanel testNGPropertiesPanel = new JPanel();
        testNGPropertiesPanel.setLayout(new BorderLayout(1, 1));
        testNGPropertiesPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        outputDirectory = new LabeledComponent<TextFieldWithBrowseButton>();
        outputDirectory.setText("&Output Directory:");
        TextFieldWithBrowseButton outputDirectoryButton = new TextFieldWithBrowseButton();
        outputDirectory.setComponent(outputDirectoryButton);
        outputDirectoryButton.addBrowseFolderListener(
                "TestNG",
                "Select test output directory", project,
                new FileChooserDescriptor(false, true, false, false, false, false));

        bottom.add(outputDirectory, new GridConstraints(3, 0, 1, 1, ANCHOR_WEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, 3, null, null, null));

        propertiesFile = new LabeledComponent<TextFieldWithBrowseButton>();
        propertiesFile.setText("&Properties file:");
        TextFieldWithBrowseButton textFieldWithBrowseButton = new TextFieldWithBrowseButton();
        propertiesFile.setComponent(textFieldWithBrowseButton);

        FileChooserDescriptor propertiesFileDescriptor = new FileChooserDescriptor(true,
                                                                                   false,
                                                                                   false,
                                                                                   false,
                                                                                   false,
                                                                                   false)
        {
            @Override
            public boolean isFileVisible(VirtualFile virtualFile, boolean showHidden) {
                if (!showHidden && virtualFile.getName().charAt(0) == '.') return false;
                return virtualFile.isDirectory() || "properties".equals(virtualFile.getExtension());
            }
        };

        textFieldWithBrowseButton.addBrowseFolderListener("TestNG", "Select .properties file for test properties", project, propertiesFileDescriptor);

        testNGPropertiesPanel.add(propertiesFile, BorderLayout.NORTH);

        JPanel testNGPropertiesTablePanel = new JPanel();
        testNGPropertiesTablePanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        propertiesTableView = new TableView(propertiesTableModel);
        propertiesTableView.setShowGrid(true);
        JScrollPane pane = ScrollPaneFactory.createScrollPane(propertiesTableView);
        pane.setPreferredSize(new Dimension(pane.getPreferredSize().width, 200));
        testNGPropertiesTablePanel.add(pane, new GridConstraints(0, 0, 3, 1, ANCHOR_NORTH, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, null, null, null));

        JButton addParameterButton = new JButton("Add");
        addParameterButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                propertiesTableModel.addParameter();
            }
        });
        JButton removeParameterButton = new JButton("Remove");
        removeParameterButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                for (int row : propertiesTableView.getSelectedRows()) {
                    propertiesTableModel.removeProperty(row);
                }
            }
        });

        testNGPropertiesTablePanel.add(addParameterButton, new GridConstraints(0, 1, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));

        testNGPropertiesTablePanel.add(removeParameterButton, new GridConstraints(1, 1, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null));
        testNGPropertiesTablePanel.add(new Spacer(), new GridConstraints(2, 1, 1, 1, ANCHOR_CENTER, FILL_VERTICAL, 1, SIZEPOLICY_CAN_GROW, null, null, null));

        propertiesScrollPane.setComponent(testNGPropertiesTablePanel);

        testNGPropertiesPanel.add(testNGPropertiesTablePanel, BorderLayout.CENTER);

        propertyTabs.addTab("JDK Settings", customJDKPanel);
        propertyTabs.addTab("Test Parameters", testNGPropertiesPanel);

        panel.add(propertyTabs.getComponent(), new GridConstraints(6, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK, null, null, null));

    }

    @Override
    protected void disposeEditor() {
    }

    public void onTypeChanged(TestType type) {
        //LOGGER.info("onTypeChanged with " + type);
        if (type != TestType.PACKAGE && type != TestType.SUITE)
            moduleClasspath.setEnabled(true);
        else
            evaluateModuleClassPath();
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

}