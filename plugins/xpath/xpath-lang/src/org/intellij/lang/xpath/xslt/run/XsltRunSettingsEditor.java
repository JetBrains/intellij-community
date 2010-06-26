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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.Table;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.intellij.lang.xpath.xslt.associations.impl.AnyXMLDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.List;

class XsltRunSettingsEditor extends SettingsEditor<XsltRunConfiguration> {
    static final boolean ALLOW_CHOOSING_SDK = !(StdFileTypes.JAVA instanceof PlainTextFileType);
    private final Project myProject;

    private Editor myEditor;

    static class AddParameterAction extends AnAction {
        private final JTable myParams;

        public AddParameterAction(JTable params) {
            super("Add", "Add Parameter", IconLoader.getIcon("/general/add.png"));
            myParams = params;
        }

        public void actionPerformed(AnActionEvent e) {
            ((Editor.ParamTableModel)myParams.getModel()).addParam();
        }
    }

    static class RemoveParameterAction extends AnAction {
        private final JTable myParams;

        public RemoveParameterAction(Table paramModel) {
            super("Remove", "Remove Parameter", IconLoader.getIcon("/general/remove.png"));
            myParams = paramModel;
        }

        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(myParams.getRowCount() > 0 && myParams.getSelectedRow() != -1);
        }

        public void actionPerformed(AnActionEvent e) {
            ((Editor.ParamTableModel)myParams.getModel()).removeParam(myParams.getSelectedRow());
        }
    }

    static class Editor {
        private JTabbedPane myComponent;

        private TextFieldWithBrowseButton myXsltFile;
        private ComboboxWithBrowseButton myXmlInputFile;
        private TextFieldWithBrowseButton myOutputFile;
        private JCheckBox myOpenOutputFile;
        private JCheckBox myOpenInBrowser;
        private JPanel myParamsToolbar;
        private Table myParameters;

        private ButtonGroup myOutputOptions;
        private JRadioButton myShowInConsole;
        private JRadioButton mySaveToFile;
        private JRadioButton myShowInStdout;

        private RawCommandLineEditor myVmArguments;
        private JCheckBox mySmartErrorHandling;
        private TextFieldWithBrowseButton myWorkingDirectory;

        private ButtonGroup myJdkOptions;
        private JRadioButton myJdkChoice;
        private JRadioButton myModuleChoice;
        private ComboBox myModule;
        private ComboBox myJDK;
        private ComboBox myFileType;
        private JPanel myClasspathAndJDKPanel;

        private final AnyXMLDescriptor myXmlDescriptor;
        private final FileChooserDescriptor myXsltDescriptor;

        public Editor(final Project project) {
            final PsiManager psiManager = PsiManager.getInstance(project);

            myXsltDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
                public boolean isFileVisible(final VirtualFile file, boolean showHiddenFiles) {
                    if (file.isDirectory()) return true;
                    if (!super.isFileVisible(file, showHiddenFiles)) return false;

                    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
                        public Boolean compute() {
                            final PsiFile psiFile = psiManager.findFile(file);
                            return psiFile != null && XsltSupport.isXsltFile(psiFile);
                        }
                    });
                }
            };
            final TextComponentAccessor<JTextField> projectDefaultAccessor = new TextComponentAccessor<JTextField>() {
                public String getText(JTextField component) {
                    final String text = component.getText();
                    final VirtualFile baseDir = project.getBaseDir();
                    return text.length() > 0 ? text : (baseDir != null ? baseDir.getPresentableUrl() : "");
                }

                public void setText(JTextField component, String text) {
                    component.setText(text);
                }
            };
            myXsltFile.addBrowseFolderListener("Choose XSLT File", null, project, myXsltDescriptor, projectDefaultAccessor);
            myXsltFile.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
                final VirtualFileManager fileMgr = VirtualFileManager.getInstance();
                final FileAssociationsManager associationsManager = FileAssociationsManager.getInstance(project);
                protected void textChanged(DocumentEvent e) {
                    final String text = myXsltFile.getText();
                    final JComboBox comboBox = myXmlInputFile.getComboBox();
                    final Object oldXml = getXmlInputFile();
                    if (text.length() != 0) {
                        final ComboBoxModel model = comboBox.getModel();

                        boolean found = false;
                        for (int i=0; i<model.getSize(); i++) {
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
                            comboBox.setModel(new DefaultComboBoxModel(associations));
                        }
                        if (!found) {
                            comboBox.getEditor().setItem(oldXml);
                        }
                        comboBox.setSelectedItem(oldXml);
                    } else {
                        comboBox.setModel(new DefaultComboBoxModel(ArrayUtil.EMPTY_OBJECT_ARRAY));
                        comboBox.getEditor().setItem(oldXml);
                    }
                }
            });

            myXmlInputFile.getComboBox().setEditable(true);

            myXmlDescriptor = new AnyXMLDescriptor();
            myXmlInputFile.addBrowseFolderListener("Choose XML File", null, project, myXmlDescriptor, new TextComponentAccessor<JComboBox>() {
                public String getText(JComboBox comboBox) {
                    Object item = comboBox.getEditor().getItem();
                    if (item.toString().length() == 0) {
                        final String text = projectDefaultAccessor.getText(myXsltFile.getChildComponent());
                        final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(text.replace(File.separatorChar, '/')));
                        if (file != null && !file.isDirectory()) {
                            final VirtualFile parent = file.getParent();
                            assert parent != null;
                            return parent.getPresentableUrl();
                        }
                    }
                    return item.toString();
                }

                public void setText(JComboBox comboBox, String text) {
                    comboBox.getEditor().setItem(text);
                }
            });

            myOutputFile.addBrowseFolderListener("Choose Output File", "The selected file will be overwritten during execution.",
                    project, new FileChooserDescriptor(true, true, false, false, false, false));

            final ItemListener outputStateListener = new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    updateOutputState();
                }
            };
            myShowInConsole.addItemListener(outputStateListener);
            myShowInStdout.addItemListener(outputStateListener);
            mySaveToFile.addItemListener(outputStateListener);
            myClasspathAndJDKPanel.setVisible(ALLOW_CHOOSING_SDK);
            updateOutputState();

            myFileType.setRenderer(new FileTypeRenderer() {
                public Component getListCellRendererComponent(JList jList, Object object, int i, boolean b, boolean b1) {
                    if (object == null) {
                        super.getListCellRendererComponent(jList, StdFileTypes.ARCHIVE, i, b, b1);
                        setIcon(IconLoader.getIcon("/actions/cancel.png"));
                        setText("Disabled");
                        return this;
                    }
                    return super.getListCellRendererComponent(jList, object, i, b, b1);
                }
            });
            myFileType.setModel(new DefaultComboBoxModel(getFileTypes(project)));

            myParameters.setModel(new ParamTableModel());
            myParameters.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    setForeground(null);
                    setToolTipText(null);
                    if (column == 0) {
                        if (table.getModel().getValueAt(row, 1) == null) {
                            setForeground(Color.BLUE);
                            setToolTipText("No value set for parameter '" + value + "'");
                        }
                    } else if (value == null) {
                        value = "<none>";
                    }
                    return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                }
            });

            final Module[] modules = ModuleManager.getInstance(project).getModules();
            myModule.setModel(new DefaultComboBoxModel(ArrayUtil.mergeArrays(new Object[]{ "<default>" }, modules, Object.class)));
            myModule.setRenderer(new DefaultListCellRenderer() {
                public Component getListCellRendererComponent(JList list, final Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Module) {
                        final Module module = (Module)value;
                        setText(ApplicationManager.getApplication().runReadAction(new Computable<String>() {
                            public String compute() {
                                return module.getName();
                            }
                        }));
                        setIcon(module.getModuleType().getNodeIcon(true));
                    } else if (value instanceof String) {
                        setText((String)value);
                    }
                    return this;
                }
            });

            final Sdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
            myJDK.setModel(new DefaultComboBoxModel(allJdks));
            if (allJdks.length > 0) {
                myJDK.setSelectedIndex(0);
            } else {
                myJdkChoice.setEnabled(false);
                myJDK.setEnabled(false);
            }
            myJDK.setRenderer(new DefaultListCellRenderer() {
                public Component getListCellRendererComponent(JList list, final Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value != null) {
                        final Sdk jdk = (Sdk)value;
                        setText(ApplicationManager.getApplication().runReadAction(new Computable<String>() {
                            public String compute() {
                                return jdk.getName();
                            }
                        }));
                        setIcon(jdk.getSdkType().getIcon());
                    }
                    return this;
                }
            });

            final ItemListener updateListener = new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    updateJdkState();
                }
            };
            myModuleChoice.addItemListener(updateListener);
            myJdkChoice.addItemListener(updateListener);
            updateJdkState();

            myWorkingDirectory.addBrowseFolderListener("Working Directory", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor());

            final DefaultActionGroup group = new DefaultActionGroup();
            group.add(new AddParameterAction(myParameters));
            group.add(new RemoveParameterAction(myParameters));
            final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("XsltRunSettingsEditor", group, true);
            myParamsToolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
            myParamsToolbar.add(actionToolbar.getComponent());

            myVmArguments.setDialogCaption("VM Arguments");
        }

        private static Vector<FileType> getFileTypes(Project project) {
            final Vector<FileType> v = new Vector<FileType>();

            final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
            for (FileType fileType : fileTypes) {
                // get rid of file types useless for highlighting
                if (fileType == StdFileTypes.CLASS ||
                        fileType == StdFileTypes.IDEA_PROJECT ||
                        fileType == StdFileTypes.IDEA_WORKSPACE ||
                        fileType == StdFileTypes.IDEA_MODULE ||
                        fileType == StdFileTypes.GUI_DESIGNER_FORM)
                {
                    continue;
                }

                try {
                    if (fileType instanceof LanguageFileType) {
                        final SyntaxHighlighter sh = SyntaxHighlighterFactory.getSyntaxHighlighter(((LanguageFileType)fileType).getLanguage(), project, null);
                        if (sh != null) {
                            v.add(fileType);                            
                        }
                    }
                } catch (Throwable e) {
                    Logger.getInstance(XsltRunSettingsEditor.class.getName()).info("Encountered incompatible FileType: " + fileType.getName(), e);
                }
            }
            Collections.sort(v, new Comparator<FileType>() {
                public int compare(FileType o1, FileType o2) {
                    return o1.getDescription().compareTo(o2.getDescription());
                }
            });

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
                } else {
                    myXmlDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, s.getModule());
                }
            } else {
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
            } else {
                final VirtualFile xsltFile = s.findXsltFile();
                if (xsltFile != null) {
                    myXsltDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, ProjectRootManager.getInstance(s.getProject()).getFileIndex().getModuleForFile(xsltFile));
                }
                myModule.setSelectedIndex(0);
            }
            myJDK.setSelectedItem(s.getJdk());
            mySmartErrorHandling.setSelected(s.mySmartErrorHandling);
            setSelectedIndex(myOutputOptions, s.getOutputType().ordinal());
            setSelectedIndex(myJdkOptions, s.getJdkChoice().ordinal());
        }

        public void applyTo(XsltRunConfiguration s) {
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
            s.setJDK((Sdk)myJDK.getSelectedItem());
            s.setJdkChoice(XsltRunConfiguration.JdkChoice.values()[getSelectedIndex(myJdkOptions)]);
            s.mySmartErrorHandling = mySmartErrorHandling.isSelected();
            s.setOutputType(XsltRunConfiguration.OutputType.values()[getSelectedIndex(myOutputOptions)]);
        }

        private void updateOutputState() {
            final int selectedIndex = getSelectedIndex(myOutputOptions);
            if (selectedIndex == XsltRunConfiguration.OutputType.FILE.ordinal()) {
                myOutputFile.setEnabled(true);
                myOpenOutputFile.setEnabled(true);
                myOpenInBrowser.setEnabled(true);

                myFileType.setEnabled(false);
            } else {
                myOutputFile.setEnabled(false);
                myOpenOutputFile.setEnabled(false);
                myOpenInBrowser.setEnabled(false);

                final boolean b = selectedIndex == XsltRunConfiguration.OutputType.CONSOLE.ordinal();
                myFileType.setEnabled(b);
            }
        }

        private void updateJdkState() {
            myModule.setEnabled(getSelectedIndex(myJdkOptions) == XsltRunConfiguration.JdkChoice.FROM_MODULE.ordinal());
            myJDK.setEnabled(getSelectedIndex(myJdkOptions) == XsltRunConfiguration.JdkChoice.JDK.ordinal());
        }

        @Nullable
        private Module getModule() {
            final Object selectedItem = myModule.getSelectedItem();
            return selectedItem instanceof Module ? (Module)selectedItem : null;
        }

        private String getXmlInputFile() {
            final JComboBox comboBox = myXmlInputFile.getComboBox();
            final Object currentItem = comboBox.getEditor().getItem();
            String s = (String)(currentItem != null ? currentItem : comboBox.getSelectedItem());
            return s != null ? s : "";
        }

        private void setSelectedIndex(ButtonGroup group, int i) {
            final Enumeration<AbstractButton> buttons = group.getElements();
            //noinspection ForLoopThatDoesntUseLoopVariable
            for (int j = 0; buttons.hasMoreElements(); j++) {
                group.setSelected(buttons.nextElement().getModel(), i == j);
            }
        }

        private int getSelectedIndex(ButtonGroup group) {
            final ButtonModel selection = group.getSelection();
            if (selection == null) return -1;
            final Enumeration<AbstractButton> buttons = group.getElements();
            //noinspection ForLoopThatDoesntUseLoopVariable
            for (int i = 0; buttons.hasMoreElements(); i++) {
                final AbstractButton button = buttons.nextElement();
                if (group.isSelected(button.getModel())) return i;
            }
            return -1;
        }

        private static class ParamTableModel extends AbstractTableModel {
            static class Param {
                public String name;
                public String value;

                public Param(String name, String value) {
                    this.name = name;
                    this.value = value;
                }

                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;

                    final Param param = (Param)o;

                    return name.equals(param.name) && value.equals(param.value);
                }

                public int hashCode() {
                    int result = name.hashCode();
                    result = 29 * result + value.hashCode();
                    return result;
                }
            }

            private final List<Param> myParams = new ArrayList<Param>();

            public ParamTableModel() {
            }

            public ParamTableModel(List<Pair<String, String>> params) {
                for (Pair<String, String> pair : params) {
                    myParams.add(new Param(pair.getFirst(), pair.getSecond()));
                }
            }

            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }

            public int getRowCount() {
                return myParams.size();
            }

            public int getColumnCount() {
                return 2;
            }

            public String getColumnName(int column) {
                return column == 0 ? "Name" : "Value";
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return true;
            }

            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                // can happen if param is deleted while editing it
                if (rowIndex >= myParams.size()) return;

                final Param param = myParams.get(rowIndex);
                final String value = (String)aValue;
                if (columnIndex == 0) {
                    param.name = value;
                } else {
                    param.value = value;
                }
                fireTableCellUpdated(rowIndex, columnIndex);
            }

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
                final ArrayList<Pair<String, String>> pairs = new ArrayList<Pair<String, String>>(myParams.size());
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

    public XsltRunSettingsEditor(Project project) {
        myProject = project;
    }

    protected void resetEditorFrom(XsltRunConfiguration s) {
        myEditor.resetFrom(s);
    }

    protected void applyEditorTo(XsltRunConfiguration s) throws ConfigurationException {
        myEditor.applyTo(s);
    }

    @NotNull
    protected JComponent createEditor() {
        myEditor = new Editor(myProject);
        return myEditor.getComponent();
    }

    protected void disposeEditor() {
        myEditor = null;
    }
}
