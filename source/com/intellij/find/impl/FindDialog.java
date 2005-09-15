
package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PatternUtil;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class FindDialog extends DialogWrapper {
  private ComboBox myInputComboBox;
  private ComboBox myReplaceComboBox;
  private StateRestoringCheckBox myCbCaseSensitive;
  private StateRestoringCheckBox myCbPreserveCase;
  private StateRestoringCheckBox myCbWholeWordsOnly;
  private StateRestoringCheckBox myCbRegularExpressions;
  private JRadioButton myRbGlobal;
  private JRadioButton myRbSelectedText;
  private JRadioButton myRbForward;
  private JRadioButton myRbBackward;
  private JRadioButton myRbFromCursor;
  private JRadioButton myRbEntireScope;
  private JRadioButton myRbProject;
  private JRadioButton myRbDirectory;
  private JRadioButton myRbModule;
  private ComboBox myModuleComboBox;
  private ComboBox myDirectoryComboBox;
  private StateRestoringCheckBox myCbWithSubdirectories;
  private JLabel myReplacePrompt;
  private JCheckBox myCbToOpenInNewTab;
  private final FindModel myModel;
  private FixedSizeButton mySelectDirectoryButton;
  private StateRestoringCheckBox useFileFilter;
  private ComboBox myFileFilter;
  protected JCheckBox myCbToSkipResultsWhenOneUsage;
  private final Project myProject;

  public FindDialog(Project project, FindModel model){
    super(project, true);
    myProject = project;
    myModel = model;

    if (myModel.isReplaceState()){
      if (myModel.isMultipleFiles()){
        setTitle("Replace in Project");
      }
      else{
        setTitle("Replace Text");
      }
    }
    else{
      setButtonsMargin(null);
      if (myModel.isMultipleFiles()){
        setTitle("Find in Path");
      }
      else{
        setTitle("Find Text");
      }
    }
    setOKButtonText("&Find");
    setOKButtonIcon(IconLoader.getIcon("/actions/find.png"));
    init();
    initByModel();
  }

  public JComponent getPreferredFocusedComponent() {
    return myInputComboBox;
  }

  protected String getDimensionServiceKey() {
    return myModel.isReplaceState() ? "replaceTextDialog" : "findTextDialog";
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel prompt = new JLabel("Text to find:");
    prompt.setDisplayedMnemonic('T');
    panel.add(prompt, gbConstraints);

    myInputComboBox = new ComboBox(300);
    initCombobox(myInputComboBox);

    if (myModel.isReplaceState()){
      myReplaceComboBox = new ComboBox(300);
      initCombobox(myReplaceComboBox);
      final Component editorComponent = myReplaceComboBox.getEditor().getEditorComponent();
      editorComponent.addFocusListener(
        new FocusAdapter() {
          public void focusGained(FocusEvent e) {
            myReplaceComboBox.getEditor().selectAll();
            editorComponent.removeFocusListener(this);
          }
        }
      );
    }


    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    panel.add(myInputComboBox, gbConstraints);
    prompt.setLabelFor(myInputComboBox.getEditor().getEditorComponent());

    if (myModel.isReplaceState()){
      gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
      gbConstraints.fill = GridBagConstraints.VERTICAL;
      gbConstraints.weightx = 0;
      myReplacePrompt = new JLabel("Replace with:");
      myReplacePrompt.setDisplayedMnemonic('R');
      panel.add(myReplacePrompt, gbConstraints);

      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gbConstraints.fill = GridBagConstraints.BOTH;
      gbConstraints.weightx = 1;
      panel.add(myReplaceComboBox, gbConstraints);
      myReplacePrompt.setLabelFor(myReplaceComboBox.getEditor().getEditorComponent());
    }

    return panel;
  }

  private void initCombobox(final ComboBox comboBox) {
    comboBox.setEditable(true);
    comboBox.setMaximumRowCount(8);

    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateFindButton();
      }
    });

    Component editorComponent = comboBox.getEditor().getEditorComponent();
    editorComponent.addKeyListener(
      new KeyAdapter() {
        public void keyReleased(KeyEvent e) {
          Object item = comboBox.getEditor().getItem();
          if (item != null && !item.equals(comboBox.getSelectedItem())){
            int caretPosition = getCaretPosition(comboBox);
            comboBox.setSelectedItem(item);
            setCaretPosition(comboBox, caretPosition);
          }
          validateFindButton();
        }
      }
    );
  }

  private static int getCaretPosition(JComboBox comboBox) {
    Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField){
      JTextField textField = (JTextField)editorComponent;
      return textField.getCaretPosition();
    }
    return 0;
  }

  private static void setCaretPosition(JComboBox comboBox, int position) {
    Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField){
      JTextField textField = (JTextField)editorComponent;
      textField.setCaretPosition(position);
    }
  }

  private void validateFindButton() {
    if (getStringToFind() == null || getStringToFind().length() == 0){
      setOKActionEnabled(false);
      return;
    }
    if (myRbDirectory != null && myRbDirectory.isSelected() &&
      (getDirectory() == null || getDirectory().length() == 0)){
      setOKActionEnabled(false);
      return;
    }
    setOKActionEnabled(true);
  }

  public JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setLayout(new GridBagLayout());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    JPanel topOptionsPanel = new JPanel();

    topOptionsPanel.setLayout(new GridLayout(1, 2, 8, 0));
    optionsPanel.add(topOptionsPanel, gbConstraints);

    topOptionsPanel.add(createFindOptionsPanel());
    if (!myModel.isMultipleFiles()){
      topOptionsPanel.add(createDirectionPanel());
      gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
      JPanel bottomOptionsPanel = new JPanel();
      bottomOptionsPanel.setLayout(new GridLayout(1, 2, 8, 0));
      optionsPanel.add(bottomOptionsPanel, gbConstraints);
      bottomOptionsPanel.add(createScopePanel());
      bottomOptionsPanel.add(createOriginPanel());
    }
    else{
      optionsPanel.add(createGlobalScopePanel(), gbConstraints);
      gbConstraints.weightx = 1;
      gbConstraints.weighty = 1;
      gbConstraints.fill = GridBagConstraints.BOTH;

      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      optionsPanel.add(createFilterPanel(),gbConstraints);

      if (!myModel.isReplaceState()) {
        myCbToSkipResultsWhenOneUsage = new StateRestoringCheckBox(
                                          "Skip results tab with one usage",
                                          FindSettings.getInstance().isSkipResultsWithOneUsage()
                                        );
        myCbToSkipResultsWhenOneUsage.setMnemonic('k');
        optionsPanel.add(myCbToSkipResultsWhenOneUsage, gbConstraints);
      }
    }

    if (myModel.isOpenInNewTabVisible()){
      JPanel openInNewTabWindowPanel = new JPanel(new BorderLayout());
      myCbToOpenInNewTab = new JCheckBox("Open in new tab");
      myCbToOpenInNewTab.setMnemonic('b');
      myCbToOpenInNewTab.setFocusable(false);
      myCbToOpenInNewTab.setSelected(myModel.isOpenInNewTab());
      myCbToOpenInNewTab.setEnabled(myModel.isOpenInNewTabEnabled());
      openInNewTabWindowPanel.add(myCbToOpenInNewTab, BorderLayout.EAST);
      optionsPanel.add(openInNewTabWindowPanel, gbConstraints);
    }

    return optionsPanel;
  }

  private JComponent createFilterPanel() {
    JPanel filterPanel = new JPanel();
    filterPanel.setLayout(new BorderLayout());
    filterPanel.setBorder(IdeBorderFactory.createTitledBorder("File name filter"));

    myFileFilter = new ComboBox(100);
    initCombobox(myFileFilter);
    filterPanel.add(useFileFilter = new StateRestoringCheckBox("File mask"),BorderLayout.WEST);
    useFileFilter.setMnemonic('m');
    filterPanel.add(myFileFilter,BorderLayout.CENTER);
    myFileFilter.setEditable(true);
    String[] fileMasks = FindSettings.getInstance().getRecentFileMasks();
    for(int i=fileMasks.length-1; i >= 0; i--) {
      myFileFilter.addItem(fileMasks [i]);
    }
    myFileFilter.setEnabled(false);

    useFileFilter.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (!useFileFilter.isSelected()) {
            myFileFilter.setEnabled(false);
          } else {
            myFileFilter.setEnabled(true);
            myFileFilter.getEditor().selectAll();
            myFileFilter.getEditor().getEditorComponent().requestFocusInWindow();
          }
        }
      }
    );

    return filterPanel;
  }

  public void doOKAction(){
    FindModel validateModel = (FindModel)myModel.clone();
    doApply(validateModel);
    if (!validateModel.isProjectScope() && myDirectoryComboBox != null && validateModel.getModuleName()==null) {
      PsiDirectory directory = FindInProjectUtil.getPsiDirectory(validateModel, myProject);
      if (directory == null) {
        Messages.showMessageDialog(
          myProject,
          "Directory " + validateModel.getDirectoryName() + " is not found",
          "Error",
          Messages.getErrorIcon()
        );
        return;
      }
    }

    if (validateModel.isRegularExpressions()) {
      String toFind = validateModel.getStringToFind();
      try {
        if (validateModel.isCaseSensitive()){
          Pattern.compile(toFind, Pattern.MULTILINE);
        }
        else{
          Pattern.compile(toFind, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        }
      }
      catch(PatternSyntaxException e){
        Messages.showMessageDialog(
            myProject,
            "Bad pattern \"" + toFind + "\"",
            "Information",
            Messages.getErrorIcon()
        );
        return;
      }
    }

    myModel.setFileFilter( null );

    if (useFileFilter!=null && useFileFilter.isSelected() &&
        myFileFilter.getSelectedItem()!=null
       ) {
      final String mask = (String)myFileFilter.getSelectedItem();

      if(mask.length() > 0) {
        try {
          Pattern.compile(PatternUtil.convertToRegex(mask));
          myModel.setFileFilter( mask );
        } catch(PatternSyntaxException ex) {
          Messages.showMessageDialog(
            myProject,
            "Bad file mask \"" + myFileFilter.getSelectedItem() + "\"",
            "Information",
            Messages.getErrorIcon()
          );
          return;
        }
      } else {
        Messages.showMessageDialog(
          myProject,
          "Empty file mask",
          "Information",
          Messages.getErrorIcon()
        );
        return;
      }
    }

    if (myCbToSkipResultsWhenOneUsage != null){
      FindSettings.getInstance().setSkipResultsWithOneUsage(
        isSkipResultsWhenOneUsage()
      );
    }

    super.doOKAction();
  }

  public boolean isSkipResultsWhenOneUsage() {
    return myCbToSkipResultsWhenOneUsage!=null &&
    myCbToSkipResultsWhenOneUsage.isSelected();
  }

  private JPanel createFindOptionsPanel() {
    JPanel findOptionsPanel = new JPanel();
    findOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder("Options"));
    findOptionsPanel.setLayout(new BoxLayout(findOptionsPanel, BoxLayout.Y_AXIS));

    myCbCaseSensitive = new StateRestoringCheckBox("Case sensitive");
    myCbCaseSensitive.setMnemonic('C');
    findOptionsPanel.add(myCbCaseSensitive);
    if (myModel.isReplaceState()) {
      myCbPreserveCase = new StateRestoringCheckBox("Preserve case");
      myCbPreserveCase.setMnemonic('P');
      findOptionsPanel.add(myCbPreserveCase);
    }
    myCbWholeWordsOnly = new StateRestoringCheckBox("Whole words only");
    myCbWholeWordsOnly.setMnemonic('W');

    findOptionsPanel.add(myCbWholeWordsOnly);

    myCbRegularExpressions = new StateRestoringCheckBox("Regular expressions");
    myCbRegularExpressions.setMnemonic('e');
    findOptionsPanel.add(myCbRegularExpressions);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myCbRegularExpressions.addActionListener(actionListener);

    if (myModel.isReplaceState()) {
      myCbCaseSensitive.addActionListener(actionListener);
      myCbPreserveCase.addActionListener(actionListener);
    }

//    if(isReplaceState) {
//      myCbPromptOnReplace = new JCheckBox("Prompt on replace", true);
//      myCbPromptOnReplace.setMnemonic('P');
//      findOptionsPanel.add(myCbPromptOnReplace);
//    }
    return findOptionsPanel;
  }

  private void updateControls() {
    if (myCbRegularExpressions.isSelected()) {
      myCbWholeWordsOnly.makeUnselectable(false);
    } else {
      myCbWholeWordsOnly.makeSelectable();
    }
    if (myModel.isReplaceState()) {
      if (myCbRegularExpressions.isSelected() || myCbCaseSensitive.isSelected()) {
        myCbPreserveCase.makeUnselectable(false);
      } else {
        myCbPreserveCase.makeSelectable();
      }

      if (myCbPreserveCase.isSelected()) {
        myCbRegularExpressions.makeUnselectable(false);
        myCbCaseSensitive.makeUnselectable(false);
      } else {
        myCbRegularExpressions.makeSelectable();
        myCbCaseSensitive.makeSelectable();
      }
    }

    if (!myModel.isMultipleFiles()) {
      myRbFromCursor.setEnabled(myRbGlobal.isSelected());
      myRbEntireScope.setEnabled(myRbGlobal.isSelected());
    }
  }

  private JPanel createDirectionPanel() {
    JPanel directionPanel = new JPanel();
    directionPanel.setBorder(IdeBorderFactory.createTitledBorder("Direction"));
    directionPanel.setLayout(new BoxLayout(directionPanel, BoxLayout.Y_AXIS));

    myRbForward = new JRadioButton("Forward", true);
    myRbForward.setMnemonic('o');
    directionPanel.add(myRbForward);
    myRbBackward = new JRadioButton("Backward");
    myRbBackward.setMnemonic('B');
    directionPanel.add(myRbBackward);
    ButtonGroup bgDirection = new ButtonGroup();
    bgDirection.add(myRbForward);
    bgDirection.add(myRbBackward);

    return directionPanel;
  }

  private JComponent createGlobalScopePanel() {
    JPanel scopePanel = new JPanel();
    scopePanel.setLayout(new GridBagLayout());
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder("Scope"));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 2;
    gbConstraints.weightx = 1;
    myRbProject = new JRadioButton("Whole project", true);
    myRbProject.setMnemonic('p');
    scopePanel.add(myRbProject, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    myRbModule = new JRadioButton("Module: ", false);
    myRbModule.setMnemonic('o');
    scopePanel.add(myRbModule, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    gbConstraints.weightx = 1;
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    String names[] = new String[modules.length];
    for (int i = 0; i < modules.length; i++) {
      names[i] = modules[i].getName();
    }

    Arrays.sort(names,String.CASE_INSENSITIVE_ORDER);
    myModuleComboBox = new ComboBox(names, -1);
    scopePanel.add(myModuleComboBox, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    myRbDirectory = new JRadioButton("Directory: ", false);
    myRbDirectory.setMnemonic('D');
    scopePanel.add(myRbDirectory, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 1;

    myDirectoryComboBox = new ComboBox(-1);
    Component editorComponent = myDirectoryComboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField) {
      JTextField field = (JTextField)editorComponent;
      field.setColumns(40);
    }
    initCombobox(myDirectoryComboBox);
    scopePanel.add(myDirectoryComboBox, gbConstraints);

    gbConstraints.weightx = 0;
    gbConstraints.gridx = 2;
    gbConstraints.insets = new Insets(0, 1, 0, 0);
    mySelectDirectoryButton = new FixedSizeButton(myDirectoryComboBox);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(mySelectDirectoryButton, myDirectoryComboBox);
    mySelectDirectoryButton.setMargin(new Insets(0, 0, 0, 0));
    scopePanel.add(mySelectDirectoryButton, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 3;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    gbConstraints.insets = new Insets(0, 16, 0, 0);
    myCbWithSubdirectories = new StateRestoringCheckBox("Recursively", true);
    myCbWithSubdirectories.setSelected(true);
    myCbWithSubdirectories.setMnemonic('b');
    scopePanel.add(myCbWithSubdirectories, gbConstraints);

    ButtonGroup bgScope = new ButtonGroup();
    bgScope.add(myRbDirectory);
    bgScope.add(myRbProject);
    bgScope.add(myRbModule);

    myRbProject.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
      }
    });

    myRbDirectory.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
        myDirectoryComboBox.getEditor().getEditorComponent().requestFocusInWindow();
      }
    });

    myRbModule.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
        myModuleComboBox.requestFocusInWindow();
      }
    });

    mySelectDirectoryButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
        if (files.length != 0) {
          myDirectoryComboBox.setSelectedItem(files[0].getPresentableUrl());
          validateFindButton();
        }
      }
    });

    return scopePanel;
  }

  private void validateScopeControls() {
    if (myRbProject.isSelected() || myRbModule.isSelected()) {
      myCbWithSubdirectories.makeUnselectable(false);
    } else {
      myCbWithSubdirectories.makeSelectable();
    }
    myDirectoryComboBox.setEnabled(myRbDirectory.isSelected());
    mySelectDirectoryButton.setEnabled(myRbDirectory.isSelected());

    myModuleComboBox.setEnabled(myRbModule.isSelected());
  }

  private JPanel createScopePanel() {
    JPanel scopePanel = new JPanel();
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder("Scope"));
    scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));

    myRbGlobal = new JRadioButton("Global", true);
    myRbGlobal.setMnemonic('G');
    scopePanel.add(myRbGlobal);
    myRbSelectedText = new JRadioButton("Selected text");
    myRbSelectedText.setMnemonic('S');
    scopePanel.add(myRbSelectedText);
    ButtonGroup bgScope = new ButtonGroup();
    bgScope.add(myRbGlobal);
    bgScope.add(myRbSelectedText);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myRbGlobal.addActionListener(actionListener);
    myRbSelectedText.addActionListener(actionListener);

    return scopePanel;
  }

  private JPanel createOriginPanel() {
    JPanel originPanel = new JPanel();
    originPanel.setBorder(IdeBorderFactory.createTitledBorder("Origin"));
    originPanel.setLayout(new BoxLayout(originPanel, BoxLayout.Y_AXIS));

    myRbFromCursor = new JRadioButton("From cursor", true);
    myRbFromCursor.setMnemonic('m');
    originPanel.add(myRbFromCursor);
    myRbEntireScope = new JRadioButton("Entire scope");
    myRbEntireScope.setMnemonic('n');
    originPanel.add(myRbEntireScope);
    ButtonGroup bgOrigin = new ButtonGroup();
    bgOrigin.add(myRbFromCursor);
    bgOrigin.add(myRbEntireScope);

    return originPanel;
  }

  private String getStringToFind() {
    return (String)myInputComboBox.getSelectedItem();
  }

  private String getDirectory() {
    if (myDirectoryComboBox == null){
      return null;
    }
    return (String)myDirectoryComboBox.getSelectedItem();
  }

  private void setStringsToComboBox(String[] strings, ComboBox combo, String s) {
    if (combo.getItemCount() > 0){
      combo.removeAllItems();
    }
    if (s != null && s.indexOf('\n') < 0 && (strings.length == 0 || !s.equals(strings[strings.length - 1]))) {
      combo.addItem(s);
    }
    for(int i = strings.length - 1; i >= 0; i--){
      combo.addItem(strings[i]);
    }
  }

  private void setDirectories(ArrayList strings, String directoryName) {
    if (myDirectoryComboBox == null){
      return;
    }
    if (myDirectoryComboBox.getItemCount() > 0){
      myReplaceComboBox.removeAllItems();
    }
    if (directoryName != null && directoryName.length() > 0){
      if (strings.contains(directoryName)){
        strings.remove(directoryName);
      }
      myDirectoryComboBox.addItem(directoryName);
    }
    for(int i = strings.size() - 1; i >= 0; i--){
      myDirectoryComboBox.addItem(strings.get(i));
    }
    if (myDirectoryComboBox.getItemCount() == 0){
      myDirectoryComboBox.addItem("");
    }
  }

  public void apply() {
    doApply(myModel);
  }

  private void doApply(FindModel model) {
    FindSettings findSettings = FindSettings.getInstance();
    model.setCaseSensitive(myCbCaseSensitive.isSelected());
    findSettings.setCaseSensitive(myCbCaseSensitive.isSelected());

    if (model.isReplaceState()) {
      model.setPreserveCase(myCbPreserveCase.isSelected());
      findSettings.setPreserveCase(myCbPreserveCase.isSelected());
    }

    model.setWholeWordsOnly(myCbWholeWordsOnly.isSelected());
    findSettings.setWholeWordsOnly(myCbWholeWordsOnly.isSelected());
    model.setRegularExpressions(myCbRegularExpressions.isSelected());
    findSettings.setRegularExpressions(myCbRegularExpressions.isSelected());
    model.setStringToFind((String)myInputComboBox.getSelectedItem());

    if (model.isReplaceState()){
      model.setPromptOnReplace(true);
      model.setReplaceAll(false);
      String stringToReplace = (String)myReplaceComboBox.getSelectedItem();
      if (stringToReplace == null){
        stringToReplace = "";
      }
      model.setStringToReplace(stringToReplace);
    }

    if (!model.isMultipleFiles()){
      model.setForward(myRbForward.isSelected());
      findSettings.setForward(myRbForward.isSelected());
      model.setFromCursor(myRbFromCursor.isSelected());
      findSettings.setFromCursor(myRbFromCursor.isSelected());
      model.setGlobal(myRbGlobal.isSelected());
      findSettings.setGlobal(myRbGlobal.isSelected());
    }
    else{
      if (myCbToOpenInNewTab != null){
        model.setOpenInNewTab(myCbToOpenInNewTab.isSelected());
      }

      model.setProjectScope(myRbProject.isSelected());
      model.setDirectoryName(null);
      model.setModuleName(null);

      if (myRbDirectory.isSelected()){
        String directory = getDirectory();
        model.setDirectoryName(directory == null ? "" : directory);
        model.setWithSubdirectories(myCbWithSubdirectories.isSelected());
        findSettings.setWithSubdirectories(myCbWithSubdirectories.isSelected());
      } else if (myRbModule.isSelected()) {
        model.setModuleName((String)myModuleComboBox.getSelectedItem());
      }

      if (useFileFilter.isSelected()) {
        findSettings.setFileMask(model.getFileFilter());
      } else {
        findSettings.setFileMask(null);
      }
    }
  }

  private void initByModel() {
    myCbCaseSensitive.setSelected(myModel.isCaseSensitive());
    myCbWholeWordsOnly.setSelected(myModel.isWholeWordsOnly());
    myCbRegularExpressions.setSelected(myModel.isRegularExpressions());
    if (!myModel.isMultipleFiles()){

      if (myModel.isForward()){
        myRbForward.setSelected(true);
      }
      else{
        myRbBackward.setSelected(true);
      }

      if (myModel.isFromCursor()){
        myRbFromCursor.setSelected(true);
      }
      else{
        myRbEntireScope.setSelected(true);
      }

      if (myModel.isGlobal()){
        myRbGlobal.setSelected(true);
      }
      else{
        myRbSelectedText.setSelected(true);
      }

    }
    else{
      setDirectories(FindSettings.getInstance().getRecentDirectories(), myModel.getDirectoryName());
      if (myModel.isProjectScope()){
        myRbProject.setSelected(true);

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(false);
      }
      else if (myModel.getDirectoryName()!=null) {
        myRbDirectory.setSelected(true);
        myCbWithSubdirectories.setEnabled(true);
        myDirectoryComboBox.setEnabled(true);
        mySelectDirectoryButton.setEnabled(true);
        myModuleComboBox.setEnabled(false);
      } else {
        myRbModule.setSelected(true);

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(true);
        myModuleComboBox.setSelectedItem(myModel.getModuleName());
      }

      myCbWithSubdirectories.setSelected(myModel.isWithSubdirectories());

      if (myModel.getFileFilter()!=null && myModel.getFileFilter().length() > 0) {
        myFileFilter.setSelectedItem(myModel.getFileFilter());
        myFileFilter.setEnabled(true);
        useFileFilter.setSelected(true);
      }
    }

    setStringsToComboBox(FindSettings.getInstance().getRecentFindStrings(), myInputComboBox, myModel.getStringToFind());
    if (myModel.isReplaceState()){
      myCbPreserveCase.setSelected(myModel.isPreserveCase());
      setStringsToComboBox(FindSettings.getInstance().getRecentReplaceStrings(), myReplaceComboBox, myModel.getStringToReplace());
    }
    updateControls();
    validateFindButton();
  }
}

