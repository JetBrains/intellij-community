package com.intellij.tools;

import com.intellij.ide.DataManager;
import com.intellij.ide.macro.MacroManager;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class ToolEditorDialog extends DialogWrapper {
  private final JTextField myNameField = new JTextField();
  private final JTextField myDescriptionField = new JTextField();
  private final ComboBox myGroupCombo = new ComboBox(-1);
  private final JCheckBox myShowInMainMenuCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.main.checkbox"));
  private final JCheckBox myShowInEditorCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.editor.checkbox"));
  private final JCheckBox myShowInProjectTreeCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.project.checkbox"));
  private final JCheckBox myShowInSearchResultsPopupCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.search.checkbox"));
  private final JCheckBox myUseConsoleCheckbox = new JCheckBox(ToolsBundle.message("tools.open.console.checkbox"));
  private final JCheckBox mySynchronizedAfterRunCheckbox = new JCheckBox(ToolsBundle.message("tools.synchronize.files.checkbox"));
  private boolean myEnabled;

  // command fields
  private final JTextField myTfCommandWorkingDirectory = new JTextField();
  private final JTextField myTfCommand = new JTextField();
  private final JTextField myParametersField = new JTextField();
  private JButton myInsertWorkingDirectoryMacroButton;
  private JButton myInsertCommandMacroButton;
  private JButton myInsertParametersMacroButton;

  private final JButton myOutputFiltersButton;
  // panels
  private final JPanel mySimpleProgramPanel = createCommandPane();
  private FilterInfo[] myOutputFilters;
  private final Project myProject;

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints constr;

    // name and group
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 0, 0, 0);
    panel.add(new JLabel(ToolsBundle.message("tools.name.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 0;
    constr.weightx = 1;
    constr.insets = new Insets(5, 10, 0, 10);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(myNameField, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 10, 0, 0);
    panel.add(new JLabel(ToolsBundle.message("tools.group.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 3;
    constr.gridy = 0;
    constr.weightx = 0.7;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(myGroupCombo, constr);
    myGroupCombo.setEditable(true);
    myGroupCombo.setFont(myNameField.getFont());
    Dimension comboSize = myNameField.getPreferredSize();
    myGroupCombo.setMinimumSize(comboSize);
    myGroupCombo.setPreferredSize(comboSize);

    // description

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 1;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 0, 0, 0);
    panel.add(new JLabel(ToolsBundle.message("tools.description.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 1;
    constr.weightx = 1;
    constr.gridwidth = 3;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(myDescriptionField, constr);

    // check boxes
    JPanel panel0 = new JPanel(new GridBagLayout());
    constr = new GridBagConstraints();
    constr.gridheight = 2;
    panel0.add(getShowInPanel(), constr);
    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.weightx = 1.0;
    constr.anchor = GridBagConstraints.NORTHEAST;
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.insets = new Insets(5, 10, 0, 0);
    panel0.add(myUseConsoleCheckbox, constr);
    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 1;
    constr.weightx = 1.0;
    constr.anchor = GridBagConstraints.NORTHEAST;
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.insets = new Insets(0, 10, 0, 0);
    panel0.add(mySynchronizedAfterRunCheckbox, constr);

    // Placed temporarily here, might be moved elsewhere in the future.
    panel0.add(myOutputFiltersButton);

    constr = new GridBagConstraints();
    constr.gridy = 4;
    constr.gridwidth = 4;
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.weightx = 1.0;
    constr.insets = new Insets(5, 0, 0, 0);
    panel.add(panel0, constr);

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 6;
    constr.gridwidth = 2;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(10, 0, 0, 0);
    panel.add(Box.createVerticalStrut(10), constr);

    // custom panels (put into same place)
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 7/*8*/;
    constr.gridwidth = 4;
    constr.fill = GridBagConstraints.BOTH;
    constr.weightx = 1.0;
    constr.weighty = 1.0;
    constr.anchor = GridBagConstraints.NORTH;
    panel.add(mySimpleProgramPanel, constr);

    return panel;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("preferences.externalToolsEdit");
  }

  public ToolEditorDialog(JComponent parent) {
    super(parent, true);

    myOutputFiltersButton = new JButton(ToolsBundle.message("tools.filters.button"));

    DataContext dataContext = DataManager.getInstance().getDataContext(parent);
    myProject = DataKeys.PROJECT.getData(dataContext);
    MacroManager.getInstance().cacheMacrosPreview(dataContext);
    setTitle(ToolsBundle.message("tools.edit.title"));
    init();
    addListeners();
  }

  private JPanel createCommandPane() {
    JPanel pane = new JPanel(new GridBagLayout());
    pane.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createEtchedBorder(),
        BorderFactory.createEmptyBorder(5, 5, 5, 5)
      )
    );
    GridBagConstraints constr;

    // program

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.insets = new Insets(5, 0, 0, 10);
    constr.anchor = GridBagConstraints.WEST;
    pane.add(new JLabel(ToolsBundle.message("tools.program.label")), constr);

    FixedSizeButton browseCommandButton = new FixedSizeButton(myTfCommand);
    browseCommandButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
          VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
          if (files.length != 0) {
            VirtualFile file = files[0];
            myTfCommand.setText(file.getPresentableUrl());
            String workingDirectory = myTfCommandWorkingDirectory.getText();
            if (workingDirectory == null || workingDirectory.length() == 0){
              VirtualFile parent = file.getParent();
              if (parent != null && parent.isDirectory()) {
                myTfCommandWorkingDirectory.setText(parent.getPresentableUrl());
              }
            }
          }
        }
      }
    );
    JPanel _pane0 = new JPanel(new BorderLayout());
    _pane0.add(myTfCommand, BorderLayout.CENTER);
    _pane0.add(browseCommandButton, BorderLayout.EAST);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseCommandButton, myTfCommand);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 0;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    constr.weightx = 1.0;
    pane.add(_pane0, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 0;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    myInsertCommandMacroButton = new JButton(ToolsBundle.message("tools.insert.macro.button"));
    pane.add(myInsertCommandMacroButton, constr);

    // parameters

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 1;
    constr.insets = new Insets(5, 0, 0, 0);
    constr.anchor = GridBagConstraints.WEST;
    pane.add(new JLabel(ToolsBundle.message("tools.parameters.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 1;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    constr.weightx = 1.0;
    pane.add(myParametersField, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 1;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    myInsertParametersMacroButton = new JButton(ToolsBundle.message("tools.insert.macro.button.a"));
    pane.add(myInsertParametersMacroButton, constr);

    // working directory

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 2;
    constr.insets = new Insets(5, 0, 5, 10);
    constr.anchor = GridBagConstraints.WEST;
    pane.add(new JLabel(ToolsBundle.message("tools.working.directory.label")), constr);

    FixedSizeButton browseDirectoryButton = new FixedSizeButton(myTfCommandWorkingDirectory);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseDirectoryButton, myTfCommandWorkingDirectory);
    browseDirectoryButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
          VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
          if (files.length != 0) {
            myTfCommandWorkingDirectory.setText(files[0].getPresentableUrl());
          }
        }
      }
    );
    JPanel _pane1 = new JPanel(new BorderLayout());
    _pane1.add(myTfCommandWorkingDirectory, BorderLayout.CENTER);
    _pane1.add(browseDirectoryButton, BorderLayout.EAST);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 2;
    constr.gridwidth = 1;
    constr.insets = new Insets(5, 10, 5, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    constr.weightx = 1.0;
    pane.add(_pane1, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 2;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    myInsertWorkingDirectoryMacroButton = new JButton(ToolsBundle.message("tools.insert.macro.button.c"));
    pane.add(myInsertWorkingDirectoryMacroButton, constr);

    // for normal resizing
    constr = new GridBagConstraints();
    constr.gridy = 3;
    constr.fill = GridBagConstraints.VERTICAL;
    constr.weighty = 1.0;
    pane.add(new JLabel(), constr);

    pane.setPreferredSize(new Dimension(600, 100));

    return pane;
  }

  private class InsertMacroActionListener implements ActionListener {
    private final JTextField myTextField;

    public InsertMacroActionListener(JTextField textField) {
      myTextField = textField;
    }

    public void actionPerformed(ActionEvent e) {
      MacrosDialog dialog = new MacrosDialog(myProject);
      dialog.show();
      if (dialog.isOK() && dialog.getSelectedMacro() != null) {
        String macro = dialog.getSelectedMacro().getName();
        int position = myTextField.getCaretPosition();
        try {
          myTextField.getDocument().insertString(position, "$" + macro + "$", null);
          myTextField.setCaretPosition(position + macro.length() + 2);
        }
        catch(BadLocationException ex){
        }
        myTextField.requestFocus();
      }
    }
  }

  private void addListeners() {
    myOutputFiltersButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        OutputFiltersDialog dialog = new OutputFiltersDialog(myOutputFiltersButton, getData().getOutputFilters());
        dialog.show();
        if (dialog.isOK()) {
          myOutputFilters = dialog.getData();
        }
      }
    });
    myInsertCommandMacroButton.addActionListener(new InsertMacroActionListener(myTfCommand));
    myInsertParametersMacroButton.addActionListener(new InsertMacroActionListener(myParametersField));
    myInsertWorkingDirectoryMacroButton.addActionListener(new InsertMacroActionListener(myTfCommandWorkingDirectory));

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        handleOKButton();
      }
    });
  }

  private void handleOKButton() {
    setOKActionEnabled(myNameField.getText().trim().length() > 0);
  }

  public Tool getData() {
    Tool tool = new Tool();

    tool.setName(convertString(myNameField.getText()));
    tool.setDescription(convertString(myDescriptionField.getText()));
    tool.setGroup(myGroupCombo.getSelectedItem() != null ? convertString(myGroupCombo.getSelectedItem().toString()) : null);
    tool.setShownInMainMenu(myShowInMainMenuCheckbox.isSelected());
    tool.setShownInEditor(myShowInEditorCheckbox.isSelected());
    tool.setShownInProjectViews(myShowInProjectTreeCheckbox.isSelected());
    tool.setShownInSearchResultsPopup(myShowInSearchResultsPopupCheckbox.isSelected());
    tool.setUseConsole(myUseConsoleCheckbox.isSelected());
    tool.setFilesSynchronizedAfterRun(mySynchronizedAfterRunCheckbox.isSelected());
    tool.setEnabled(myEnabled);

    tool.setWorkingDirectory(toSystemIndependentFormat(myTfCommandWorkingDirectory.getText()));
    tool.setProgram(convertString(myTfCommand.getText()));
    tool.setParameters(convertString(myParametersField.getText()));

    tool.setOutputFilters(myOutputFilters);

    return tool;
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.tools.ToolEditorDialog";
  }

  /**
    * Initialize controls
    */
  void setData(Tool tool, String[] existingGroups) {
    myNameField.setText(tool.getName());
    myDescriptionField.setText(tool.getDescription());
    if (myGroupCombo.getItemCount() > 0){
      myGroupCombo.removeAllItems();
    }
    for (int i = 0; i < existingGroups.length; i++) {
      if (existingGroups[i] != null) {
        myGroupCombo.addItem(existingGroups[i]);
      }
    }
    myGroupCombo.setSelectedItem(tool.getGroup());
    myShowInMainMenuCheckbox.setSelected(tool.isShownInMainMenu());
    myShowInEditorCheckbox.setSelected(tool.isShownInEditor());
    myShowInProjectTreeCheckbox.setSelected(tool.isShownInProjectViews());
    myShowInSearchResultsPopupCheckbox.setSelected(tool.isShownInSearchResultsPopup());
    myUseConsoleCheckbox.setSelected(tool.isUseConsole());
    mySynchronizedAfterRunCheckbox.setSelected(tool.synchronizeAfterExecution());
    myEnabled = tool.isEnabled();
    myTfCommandWorkingDirectory.setText(toCurrentSystemFormat(tool.getWorkingDirectory()));
    myTfCommand.setText(tool.getProgram());
    myParametersField.setText(tool.getParameters());
    myOutputFilters = tool.getOutputFilters();
    mySimpleProgramPanel.setVisible(true);
    handleOKButton();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private JPanel getShowInPanel() {
    JPanel panel = new JPanel(new GridLayout(2, 2, 10, 3));
    panel.setBorder(IdeBorderFactory.createTitledBorder(ToolsBundle.message("tools.menu.group")));
    panel.add(myShowInMainMenuCheckbox);
    panel.add(myShowInEditorCheckbox);
    panel.add(myShowInProjectTreeCheckbox);
    panel.add(myShowInSearchResultsPopupCheckbox);
    return panel;
  }

  private String convertString(String s) {
    if (s != null && s.trim().length() == 0) return null;
    return s;
  }

  private String toSystemIndependentFormat(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.length() == 0) return null;
    return s.replace(File.separatorChar, '/');
  }

  private String toCurrentSystemFormat(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.length() == 0) return null;
    return s.replace('/', File.separatorChar);
  }
}