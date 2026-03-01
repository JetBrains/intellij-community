/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.impl.CheckableRunConfigurationEditor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.SdkComboBox;
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Vector;

class XsltRunSettingsEditor extends SettingsEditor<XsltRunConfiguration>
  implements CheckableRunConfigurationEditor<XsltRunConfiguration> {
  static final boolean ALLOW_CHOOSING_SDK = !(StdFileTypes.JAVA instanceof PlainTextFileType);
  private final Project myProject;

  private Editor myEditor;

  @Override
  public void checkEditorData(XsltRunConfiguration s) {
    // this prevents applyTo() call with unneeded sdk model update
  }

  static class Editor implements PanelWithAnchor {
    private final JTabbedPane myComponent;

    private final TextFieldWithBrowseButton myXsltFile;
    private final ComboboxWithBrowseButton myXmlInputFile;
    private final TextFieldWithBrowseButton myOutputFile;
    private final JCheckBox myOpenOutputFile;
    private final JCheckBox myOpenInBrowser;
    private final JBTable myParameters;

    private final ButtonGroup myOutputOptions;
    private final JBRadioButton myShowInConsole;
    private final JCheckBox mySaveToFile;
    private final JRadioButton myShowInStdout;

    private final RawCommandLineEditor myVmArguments;
    private final JCheckBox mySmartErrorHandling;
    private final TextFieldWithBrowseButton myWorkingDirectory;

    private final ButtonGroup myJdkOptions;
    private final JRadioButton myJdkChoice;
    private final JRadioButton myModuleChoice;
    private final ComboBox<Object> myModule;
    private SdkComboBox myJDK;
    private final ComboBox<FileType> myFileType;
    private final JPanel myClasspathAndJDKPanel;
    private final JPanel myPanelSettings;
    private final JPanel myPanelAdvanced;
    private final JBLabel myXSLTScriptFileLabel;
    private final JPanel myParametersPanel;
    private JComponent anchor;

    private final FileChooserDescriptor myXmlDescriptor;
    private final FileChooserDescriptor myXsltDescriptor;

    Editor(Project project) {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myComponent = new JBTabbedPane();
        myPanelSettings = new JPanel();
        myPanelSettings.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        myComponent.addTab(this.$$$getMessageFromBundle$$$("messages/XPathBundle", "tab.title.settings"), myPanelSettings);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
        myPanelSettings.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
        panel1.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(null,
                                                                                  this.$$$getMessageFromBundle$$$("messages/XPathBundle",
                                                                                                                  "border.title.output"),
                                                                                  TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                  TitledBorder.DEFAULT_POSITION, null, null));
        mySaveToFile = new JCheckBox();
        this.$$$loadButtonText$$$(mySaveToFile, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "checkbox.save.to.file"));
        mySaveToFile.setToolTipText(this.$$$getMessageFromBundle$$$("messages/XPathBundle",
                                                                    "html.save.xslt.output.to.specified.file.br.em.warning.em.this.file.will.be.overwritten.without.confirmation.html"));
        panel1.add(mySaveToFile, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myOutputFile = new TextFieldWithBrowseButton();
        panel1.add(myOutputFile, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myOpenOutputFile = new JCheckBox();
        this.$$$loadButtonText$$$(myOpenOutputFile,
                                  this.$$$getMessageFromBundle$$$("messages/XPathBundle", "checkbox.open.file.in.editor.after.execution"));
        panel1.add(myOpenOutputFile, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowInConsole = new JBRadioButton();
        this.$$$loadButtonText$$$(myShowInConsole,
                                  this.$$$getMessageFromBundle$$$("messages/XPathBundle", "radio.button.show.in.extra.console.tab"));
        myShowInConsole.setToolTipText(this.$$$getMessageFromBundle$$$("messages/XPathBundle",
                                                                       "show.xslt.output.in.separate.tab.stdout.will.just.receive.xslt.error.messages"));
        panel1.add(myShowInConsole, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myShowInStdout = new JRadioButton();
        this.$$$loadButtonText$$$(myShowInStdout,
                                  this.$$$getMessageFromBundle$$$("messages/XPathBundle", "radio.button.show.in.default.console"));
        myShowInStdout.setToolTipText(this.$$$getMessageFromBundle$$$("messages/XPathBundle",
                                                                      "show.xslt.output.in.standard.run.console.mixed.with.other.error.messages"));
        panel1.add(myShowInStdout, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 4, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null,
                                               null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "label.highlight.output.as"));
        panel2.add(label1,
                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myFileType = new ComboBox();
        myFileType.setToolTipText(this.$$$getMessageFromBundle$$$("messages/XPathBundle",
                                                                  "output.highlighting.is.experimental.please.turn.it.off.if.it.s.causing.trouble"));
        panel2.add(myFileType, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
        myOpenInBrowser = new JCheckBox();
        this.$$$loadButtonText$$$(myOpenInBrowser, this.$$$getMessageFromBundle$$$("messages/XPathBundle",
                                                                                   "checkbox.open.file.in.web.browser.after.execution"));
        panel1.add(myOpenInBrowser, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel3.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
        myPanelSettings.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
        panel3.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(),
                                                                                  this.$$$getMessageFromBundle$$$("messages/XPathBundle",
                                                                                                                  "border.title.input"),
                                                                                  TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                  TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "label.choose.xml.input.file"));
        panel3.add(label2,
                   new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myXSLTScriptFileLabel = new JBLabel();
        myXSLTScriptFileLabel.setEnabled(true);
        this.$$$loadLabelText$$$(myXSLTScriptFileLabel, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "label.xslt.script.file"));
        myXSLTScriptFileLabel.setVerticalAlignment(0);
        panel3.add(myXSLTScriptFileLabel,
                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myXsltFile = new TextFieldWithBrowseButton();
        panel3.add(myXsltFile, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myXmlInputFile = new ComboboxWithBrowseButton();
        panel3.add(myXmlInputFile, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myParametersPanel = new JPanel();
        myParametersPanel.setLayout(new BorderLayout(0, 0));
        myParametersPanel.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithoutIndent");
        myPanelSettings.add(myParametersPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(-1, 150), null,
                                                                   0, false));
        myParametersPanel.setBorder(IdeBorderFactory.PlainSmallWithoutIndent.createTitledBorder(null, this.$$$getMessageFromBundle$$$(
                                                                                                  "messages/XPathBundle", "border.title.parameters"), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
                                                                                                null));
        myPanelAdvanced = new JPanel();
        myPanelAdvanced.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        myComponent.addTab(this.$$$getMessageFromBundle$$$("messages/XPathBundle", "tab.title.advanced"), myPanelAdvanced);
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "label.vm.arguments"));
        myPanelAdvanced.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null,
                                                        0, false));
        final Spacer spacer1 = new Spacer();
        myPanelAdvanced.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        mySmartErrorHandling = new JCheckBox();
        this.$$$loadButtonText$$$(mySmartErrorHandling,
                                  this.$$$getMessageFromBundle$$$("messages/XPathBundle", "checkbox.smart.error.handling"));
        myPanelAdvanced.add(mySmartErrorHandling, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                      null, null, null, 0, false));
        myClasspathAndJDKPanel = new JPanel();
        myClasspathAndJDKPanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        myClasspathAndJDKPanel.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
        myPanelAdvanced.add(myClasspathAndJDKPanel,
                            new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        myModuleChoice = new JRadioButton();
        this.$$$loadButtonText$$$(myModuleChoice, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "radio.button.from.module"));
        myClasspathAndJDKPanel.add(myModuleChoice, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                       GridConstraints.SIZEPOLICY_FIXED,
                                                                       null, null, null, 0, false));
        myModule = new ComboBox();
        myClasspathAndJDKPanel.add(myModule, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_WANT_GROW,
                                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myJdkChoice = new JRadioButton();
        this.$$$loadButtonText$$$(myJdkChoice, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "radio.button.use.jdk"));
        myClasspathAndJDKPanel.add(myJdkChoice, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "label.classpath.and.jdk"));
        myClasspathAndJDKPanel.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null,
                                                               null, 0, false));
        myVmArguments = new RawCommandLineEditor();
        myPanelAdvanced.add(myVmArguments, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myWorkingDirectory = new TextFieldWithBrowseButton();
        myPanelAdvanced.add(myWorkingDirectory,
                            new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        this.$$$loadLabelText$$$(label5, this.$$$getMessageFromBundle$$$("messages/XPathBundle", "label.working.directory"));
        myPanelAdvanced.add(label5, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null,
                                                        0, false));
        label2.setLabelFor(myXmlInputFile);
        myXSLTScriptFileLabel.setLabelFor(myXsltFile);
        label3.setLabelFor(myVmArguments);
        label5.setLabelFor(myWorkingDirectory);
        myOutputOptions = new ButtonGroup();
        myOutputOptions.add(myShowInConsole);
        myOutputOptions.add(myShowInStdout);
        myJdkOptions = new ButtonGroup();
        myJdkOptions.add(myModuleChoice);
        myJdkOptions.add(myJdkChoice);
      }
      var psiManager = PsiManager.getInstance(project);
      myXsltDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(XmlFileType.INSTANCE)
        .withFileFilter(file -> {
          var psiFile = psiManager.findFile(file);
          return psiFile != null && XsltSupport.isXsltFile(psiFile);
        })
        .withTitle(XPathBundle.message("dialog.title.choose.xslt.file"));

      final TextComponentAccessor<JTextField> projectDefaultAccessor = new TextComponentAccessor<>() {
        @Override
        public String getText(JTextField component) {
          final String text = component.getText();
          final VirtualFile baseDir = project.getBaseDir();
          return !text.isEmpty() ? text : (baseDir != null ? baseDir.getPresentableUrl() : "");
        }

        @Override
        public void setText(JTextField component, @NotNull String text) {
          component.setText(text);
        }
      };
      myXsltFile.addBrowseFolderListener(project, myXsltDescriptor, projectDefaultAccessor);
      myXsltFile.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        final VirtualFileManager fileMgr = VirtualFileManager.getInstance();
        final FileAssociationsManager associationsManager = FileAssociationsManager.getInstance(project);

        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          final String text = myXsltFile.getText();
          final JComboBox comboBox = myXmlInputFile.getComboBox();
          final Object oldXml = getXmlInputFile(); //NON-NLS
          if (!text.isEmpty()) {
            final ComboBoxModel model = comboBox.getModel();

            boolean found = false;
            for (int i = 0; i < model.getSize(); i++) {
              if (oldXml.equals(model.getElementAt(i))) found = true;
            }
            final VirtualFile virtualFile = fileMgr.findFileByUrl(VfsUtil.pathToUrl(text.replace(File.separatorChar, '/')));
            final PsiFile psiFile;
            if (virtualFile != null && (psiFile = psiManager.findFile(virtualFile)) != null) {
              final PsiFile[] files = associationsManager.getAssociationsFor(psiFile);

              final Object[] associations = new String[files.length];
              for (int i = 0; i < files.length; i++) {
                final VirtualFile f = files[i].getVirtualFile();
                assert f != null;
                associations[i] = f.getPath().replace('/', File.separatorChar);
              }
              comboBox.setModel(new DefaultComboBoxModel<>(associations));
            }
            if (!found) {
              comboBox.getEditor().setItem(oldXml);
            }
            comboBox.setSelectedItem(oldXml);
          }
          else {
            comboBox.setModel(new DefaultComboBoxModel<>(ArrayUtilRt.EMPTY_OBJECT_ARRAY));
            comboBox.getEditor().setItem(oldXml);
          }
        }
      });

      myXmlInputFile.getComboBox().setEditable(true);

      myXmlDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        .withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", "XML"), FileAssociationsManager.Holder.XML_FILES)
        .withTitle(XPathBundle.message("dialog.title.choose.xml.file"));
      myXmlInputFile.addBrowseFolderListener(project, myXmlDescriptor, new TextComponentAccessor<>() {
        @Override
        public String getText(JComboBox comboBox) {
          Object item = comboBox.getEditor().getItem();
          if (item.toString().isEmpty()) {
            var text = projectDefaultAccessor.getText(myXsltFile.getChildComponent());
            if (text == null) text = "";
            final VirtualFile file =
              VirtualFileManager.getInstance()
                .findFileByUrl(VfsUtil.pathToUrl(text.replace(File.separatorChar, '/')));
            if (file != null && !file.isDirectory()) {
              final VirtualFile parent = file.getParent();
              assert parent != null;
              return parent.getPresentableUrl();
            }
          }
          return item.toString();
        }

        @Override
        public void setText(JComboBox comboBox, @NotNull String text) {
          comboBox.getEditor().setItem(text);
        }
      });

      myOutputFile.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
        .withTitle(XPathBundle.message("dialog.title.choose.output.file"))
        .withDescription(XPathBundle.message("label.selected.file.will.be.overwritten.during.execution")));

      final ItemListener outputStateListener = new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          updateOutputState();
        }
      };
      myShowInConsole.addItemListener(outputStateListener);
      myShowInStdout.addItemListener(outputStateListener);
      mySaveToFile.addItemListener(outputStateListener);
      updateOutputState();

      myFileType.setRenderer(new FileTypeRenderer() {
        @Override
        public void customize(@NotNull JList<? extends FileType> list, FileType value, int index, boolean selected, boolean hasFocus) {
          if (value == null) {
            setIcon(AllIcons.Actions.Cancel);
            setText(XPathBundle.message("label.disabled"));
          }
          else {
            super.customize(list, value, index, selected, hasFocus);
          }
        }
      });
      myFileType.setModel(new DefaultComboBoxModel<>(getFileTypes(project)));

      myParameters = new JBTable(new ParamTableModel());
      myParameters.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          setForeground(null);
          setToolTipText(null);
          if (column == 0) {
            if (table.getModel().getValueAt(row, 1) == null) {
              setForeground(PlatformColors.BLUE);
              setToolTipText(XPathBundle.message("tooltip.no.value.set.for.parameter.0", value));
            }
          }
          else if (value == null) {
            value = "<none>";
          }
          return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
      });

      myParametersPanel.add(
        ToolbarDecorator.createDecorator(myParameters)
          .setAddAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
              ((Editor.ParamTableModel)myParameters.getModel()).addParam();
            }
          }).setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            ((Editor.ParamTableModel)myParameters.getModel()).removeParam(myParameters.getSelectedRow());
          }
        }).createPanel(), BorderLayout.CENTER);

      if (ALLOW_CHOOSING_SDK) {
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        myModule.setModel(new DefaultComboBoxModel<>(ArrayUtil.mergeArrays(new Object[]{"<default>"}, modules)));
        myModule.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
          if (value instanceof Module module) {
            final String moduleName = ReadAction.compute(() -> module.getName());
            label.setText(moduleName);
            label.setIcon(ModuleType.get(module).getIcon());
          }
          else if (value instanceof String) {
            label.setText((String)value);
          }
        }));
      }
      else {
        myModuleChoice.setEnabled(false);
        myModule.setEnabled(false);
        myJdkChoice.setSelected(true);
      }

      SdkComboBoxModel model = SdkComboBoxModel.createJdkComboBoxModel(project, new ProjectSdksModel());
      myJDK = new SdkComboBox(model);
      GridConstraints constraints = new GridConstraints();
      constraints.setColumn(2);
      constraints.setRow(1);
      constraints.setFill(GridConstraints.FILL_BOTH);
      myClasspathAndJDKPanel.add(myJDK, constraints);

      final ItemListener updateListener = new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          updateJdkState();
        }
      };
      myModuleChoice.addItemListener(updateListener);
      myJdkChoice.addItemListener(updateListener);
      updateJdkState();

      myWorkingDirectory.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle(XPathBundle.message("dialog.title.working.directory")));

      myVmArguments.setDialogCaption("VM Arguments");

      myPanelSettings.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
      myPanelAdvanced.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));

      setAnchor(myShowInConsole);
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
    public JComponent $$$getRootComponent$$$() { return myComponent; }

    @Override
    public JComponent getAnchor() {
      return anchor;
    }

    @Override
    public void setAnchor(@Nullable JComponent anchor) {
      this.anchor = anchor;
      myXSLTScriptFileLabel.setAnchor(anchor);
      myShowInConsole.setAnchor(anchor);
    }

    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    private static Vector<FileType> getFileTypes(Project project) {
      final Vector<FileType> v = new Vector<>();

      final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
      for (FileType fileType : fileTypes) {
        // get rid of file types useless for highlighting
        if (fileType.isBinary() || fileType instanceof InternalFileType) {
          continue;
        }

        try {
          if (fileType instanceof LanguageFileType) {
            final SyntaxHighlighter sh =
              SyntaxHighlighterFactory.getSyntaxHighlighter(((LanguageFileType)fileType).getLanguage(), project, null);
            v.add(fileType);
          }
        }
        catch (Throwable e) {
          Logger.getInstance(XsltRunSettingsEditor.class.getName()).info("Encountered incompatible FileType: " + fileType.getName(), e);
        }
      }
      v.sort(Comparator.comparing(FileType::getDescription));

      // off
      v.insertElementAt(null, 0);
      return v;
    }

    public void resetFrom(XsltRunConfiguration s) {
      myXsltFile.setText(s.getXsltFile());
      myXmlInputFile.getComboBox().setSelectedItem(s.getXmlInputFile());

      final VirtualFile xmlInputFile = s.findXmlInputFile();
      if (xmlInputFile != null) {
        final Module contextModule = ProjectRootManager.getInstance(s.getProject()).getFileIndex().getModuleForFile(xmlInputFile);
        if (contextModule != null) {
          myXmlDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, contextModule);
        }
        else {
          myXmlDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, s.getModule());
        }
      }
      else {
        myXmlDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, s.getModule());
      }

      myFileType.setSelectedItem(s.getFileType());
      myOutputFile.setText(s.myOutputFile);
      myOpenOutputFile.setSelected(s.myOpenOutputFile);
      myOpenInBrowser.setSelected(s.myOpenInBrowser);
      myParameters.setModel(new ParamTableModel(s.getParameters()));
      myVmArguments.setText(s.getVmArguments());
      myWorkingDirectory.setText(s.myWorkingDirectory);
      final Module module = s.getModule();
      if (module != null) {
        myModule.setSelectedItem(module);
        myXsltDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, module);
      }
      else {
        final VirtualFile xsltFile = s.findXsltFile();
        if (xsltFile != null) {
          myXsltDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT,
                                       ProjectRootManager.getInstance(s.getProject()).getFileIndex().getModuleForFile(xsltFile));
        }
        if (myModule.getItemCount() > 0) {
          myModule.setSelectedIndex(0);
        }
      }
      myJDK.getModel().getSdksModel().reset(s.getProject());
      myJDK.reloadModel();
      setSelectedSdkOrNone(myJDK, s.getJdk());
      mySmartErrorHandling.setSelected(s.mySmartErrorHandling);
      setSelectedIndex(myOutputOptions, s.getOutputType().ordinal());
      setSelectedIndex(myJdkOptions, s.getJdkChoice().ordinal());
      mySaveToFile.setSelected(s.isSaveToFile());
    }

    public void applyTo(XsltRunConfiguration s) throws ConfigurationException {
      s.setXsltFile(myXsltFile.getText());
      s.setXmlInputFile(getXmlInputFile());
      s.setFileType((FileType)myFileType.getSelectedItem());
      s.myOutputFile = myOutputFile.getText();
      s.myOpenOutputFile = myOpenOutputFile.isSelected();
      s.myOpenInBrowser = myOpenInBrowser.isSelected();
      s.setParameters(((ParamTableModel)myParameters.getModel()).getParams());
      s.setVmArguments(myVmArguments.getText());
      s.myWorkingDirectory = myWorkingDirectory.getText();
      s.setModule(getModule());
      WriteAction.run(() -> myJDK.getModel().getSdksModel().apply());
      s.setJDK(myJDK.getSelectedSdk());
      s.setJdkChoice(XsltRunConfiguration.JdkChoice.values()[getSelectedIndex(myJdkOptions)]);
      s.mySmartErrorHandling = mySmartErrorHandling.isSelected();
      s.setOutputType(XsltRunConfiguration.OutputType.values()[getSelectedIndex(myOutputOptions)]);
      s.setSaveToFile(mySaveToFile.isSelected());
    }

    private void updateOutputState() {
      myOutputFile.setEnabled(mySaveToFile.isSelected());
      myOpenOutputFile.setEnabled(mySaveToFile.isEnabled() && mySaveToFile.isSelected());
      myOpenInBrowser.setEnabled(mySaveToFile.isEnabled() && mySaveToFile.isSelected());

      final int selectedIndex = getSelectedIndex(myOutputOptions);
      final boolean b = selectedIndex == XsltRunConfiguration.OutputType.CONSOLE.ordinal();
      myFileType.setEnabled(b);
    }

    private void updateJdkState() {
      myModule.setEnabled(getSelectedIndex(myJdkOptions) == XsltRunConfiguration.JdkChoice.FROM_MODULE.ordinal());
      myJDK.setEnabled(getSelectedIndex(myJdkOptions) == XsltRunConfiguration.JdkChoice.JDK.ordinal());
    }

    private @Nullable Module getModule() {
      final Object selectedItem = myModule.getSelectedItem();
      return selectedItem instanceof Module ? (Module)selectedItem : null;
    }

    private String getXmlInputFile() {
      final JComboBox comboBox = myXmlInputFile.getComboBox();
      final Object currentItem = comboBox.getEditor().getItem();
      String s = (String)(currentItem != null ? currentItem : comboBox.getSelectedItem());
      return s != null ? s : "";
    }

    private static void setSelectedIndex(ButtonGroup group, int i) {
      final Enumeration<AbstractButton> buttons = group.getElements();
      for (int j = 0; buttons.hasMoreElements(); j++) {
        group.setSelected(buttons.nextElement().getModel(), i == j);
      }
    }

    private static int getSelectedIndex(ButtonGroup group) {
      final ButtonModel selection = group.getSelection();
      if (selection == null) return -1;
      final Enumeration<AbstractButton> buttons = group.getElements();
      for (int i = 0; buttons.hasMoreElements(); i++) {
        final AbstractButton button = buttons.nextElement();
        if (group.isSelected(button.getModel())) return i;
      }
      return -1;
    }

    private static void setSelectedSdkOrNone(@NotNull SdkComboBox comboBox, @Nullable Sdk sdk) {
      if (sdk == null) {
        comboBox.setSelectedItem(comboBox.showNoneSdkItem());
      }
      else {
        comboBox.setSelectedSdk(sdk);
      }
    }

    private static class ParamTableModel extends AbstractTableModel {
      static class Param {
        public String name;
        public String value;

        Param(String name, String value) {
          this.name = name;
          this.value = value;
        }

        @Override
        public boolean equals(Object o) {
          if (this == o) return true;
          if (o == null || getClass() != o.getClass()) return false;

          final Param param = (Param)o;

          return name.equals(param.name) && value.equals(param.value);
        }

        @Override
        public int hashCode() {
          int result = name.hashCode();
          result = 29 * result + value.hashCode();
          return result;
        }
      }

      private final List<Param> myParams = new ArrayList<>();

      ParamTableModel() {
      }

      ParamTableModel(List<? extends Pair<String, String>> params) {
        for (Pair<String, String> pair : params) {
          myParams.add(new Param(pair.getFirst(), pair.getSecond()));
        }
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return String.class;
      }

      @Override
      public int getRowCount() {
        return myParams.size();
      }

      @Override
      public int getColumnCount() {
        return 2;
      }

      @Override
      public String getColumnName(int column) {
        return column == 0 ? XPathBundle.message("attribute.descriptor.name") : XPathBundle.message("value");
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
      }

      @Override
      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        // can happen if param is deleted while editing it
        if (rowIndex >= myParams.size()) return;

        final Param param = myParams.get(rowIndex);
        final String value = (String)aValue;
        if (columnIndex == 0) {
          param.name = value;
        }
        else {
          param.value = value;
        }
        fireTableCellUpdated(rowIndex, columnIndex);
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        final Param param = myParams.get(rowIndex);
        return columnIndex == 0 ? param.name : param.value;
      }

      public void addParam() {
        myParams.add(new Param("Param" + (myParams.size() + 1), null));
        fireTableRowsInserted(myParams.size() - 1, myParams.size() - 1);
      }

      public void removeParam(int selectedRow) {
        myParams.remove(selectedRow);
        fireTableRowsDeleted(selectedRow, selectedRow);
      }

      public List<Pair<String, String>> getParams() {
        final ArrayList<Pair<String, String>> pairs = new ArrayList<>(myParams.size());
        for (Param param : myParams) {
          pairs.add(Pair.create(param.name, param.value));
        }
        return pairs;
      }
    }

    public JComponent getComponent() {
      return myComponent;
    }
  }

  XsltRunSettingsEditor(Project project) {
    myProject = project;
  }

  @Override
  protected void resetEditorFrom(@NotNull XsltRunConfiguration s) {
    myEditor.resetFrom(s);
  }

  @Override
  protected void applyEditorTo(@NotNull XsltRunConfiguration s) throws ConfigurationException {
    myEditor.applyTo(s);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    myEditor = new Editor(myProject);
    return myEditor.getComponent();
  }

  @Override
  protected void disposeEditor() {
    myEditor = null;
  }
}
