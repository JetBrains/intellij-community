
package com.intellij.find.impl;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.PatternUtil;

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

  private Action myFindAllAction;

  public FindDialog(Project project, FindModel model){
    super(project, true);
    myProject = project;
    myModel = model;

    if (myModel.isReplaceState()){
      if (myModel.isMultipleFiles()){
        setTitle(FindBundle.message("find.replace.in.project.dialog.title"));
      }
      else{
        setTitle(FindBundle.message("find.replace.text.dialog.title"));
      }
    }
    else{
      setButtonsMargin(null);
      if (myModel.isMultipleFiles()){
        setTitle(FindBundle.message("find.in.path.dialog.title"));
      }
      else{
        setTitle(FindBundle.message("find.text.dialog.title"));
      }
    }
    setOKButtonText(FindBundle.message("find.button"));
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

  @Override
  protected Action[] createActions() {
    if (!myModel.isMultipleFiles() && !myModel.isReplaceState() && myModel.isFindAllEnabled()) {
      return new Action[] { getFindAllAction(), getOKAction(), getCancelAction() };      
    }
    return super.createActions();
  }

  private Action getFindAllAction() {
    return myFindAllAction = new AbstractAction(FindBundle.message("find.all.button")) {
      public void actionPerformed(ActionEvent e) {
        doOKAction(true);
      }
    };
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel prompt = new JLabel(FindBundle.message("find.text.to.find.label"));
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
      myReplacePrompt = new JLabel(FindBundle.message("find.replace.with.label"));
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
    final String toFind = getStringToFind();

    if (toFind == null || toFind.length() == 0){
      setOKStatus(false);
      return;
    }

    if (myRbDirectory != null && myRbDirectory.isSelected() &&
      (getDirectory() == null || getDirectory().length() == 0)){
      setOKStatus(false);
      return;
    }
    setOKStatus(true);
  }

  private void setOKStatus(boolean value) {
    setOKActionEnabled(value);
    if (myFindAllAction != null) {
      myFindAllAction.setEnabled(value);
    }
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
          FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox"),
          FindSettings.getInstance().isSkipResultsWithOneUsage());
        optionsPanel.add(myCbToSkipResultsWhenOneUsage, gbConstraints);
      }
    }

    if (myModel.isOpenInNewTabVisible()){
      JPanel openInNewTabWindowPanel = new JPanel(new BorderLayout());
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
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
    filterPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.filter.file.name.group")));

    myFileFilter = new ComboBox(100);
    initCombobox(myFileFilter);
    filterPanel.add(useFileFilter = new StateRestoringCheckBox(FindBundle.message("find.filter.file.mask.checkbox")),BorderLayout.WEST);
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

  public void doOKAction() {
    doOKAction(false);
  }

  public void doOKAction(boolean findAll) {
    FindModel validateModel = (FindModel)myModel.clone();
    doApply(validateModel);
    myModel.setFindAll(findAll);
    if (!validateModel.isProjectScope() && myDirectoryComboBox != null && validateModel.getModuleName()==null) {
      PsiDirectory directory = FindInProjectUtil.getPsiDirectory(validateModel, myProject);
      if (directory == null) {
        Messages.showMessageDialog(
          myProject,
          FindBundle.message("find.directory.not.found.error", validateModel.getDirectoryName()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return;
      }
    }

    if (validateModel.isRegularExpressions()) {
      String toFind = validateModel.getStringToFind();
      try {
        Pattern pattern;

        if (validateModel.isCaseSensitive()){
          pattern = Pattern.compile(toFind, Pattern.MULTILINE);
        }
        else{
          pattern = Pattern.compile(toFind, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        }

        if (pattern.matcher("").matches()) {
          throw new PatternSyntaxException("Matching empty string",toFind,0);
        }
      }
      catch(PatternSyntaxException e){
        Messages.showMessageDialog(
            myProject,
            FindBundle.message("find.invalid.regular.expression.error", toFind),
            CommonBundle.getErrorTitle(),
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
            FindBundle.message("find.filter.invalid.file.mask.error", myFileFilter.getSelectedItem()),
            CommonBundle.getErrorTitle(),
            Messages.getErrorIcon()
          );
          return;
        }
      } else {
        Messages.showMessageDialog(
          myProject,
          FindBundle.message("find.filter.empty.file.mask.error"),
          CommonBundle.getErrorTitle(),
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
    findOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.options.group")));
    findOptionsPanel.setLayout(new BoxLayout(findOptionsPanel, BoxLayout.Y_AXIS));

    myCbCaseSensitive = new StateRestoringCheckBox(FindBundle.message("find.options.case.sensitive"));
    findOptionsPanel.add(myCbCaseSensitive);
    if (myModel.isReplaceState()) {
      myCbPreserveCase = new StateRestoringCheckBox(FindBundle.message("find.options.replace.preserve.case"));
      findOptionsPanel.add(myCbPreserveCase);
    }
    myCbWholeWordsOnly = new StateRestoringCheckBox(FindBundle.message("find.options.whole.words.only"));

    findOptionsPanel.add(myCbWholeWordsOnly);

    myCbRegularExpressions = new StateRestoringCheckBox(FindBundle.message("find.options.regular.expressions"));
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
    directionPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.direction.group")));
    directionPanel.setLayout(new BoxLayout(directionPanel, BoxLayout.Y_AXIS));

    myRbForward = new JRadioButton(FindBundle.message("find.direction.forward.radio"), true);
    directionPanel.add(myRbForward);
    myRbBackward = new JRadioButton(FindBundle.message("find.direction.backward.radio"));
    directionPanel.add(myRbBackward);
    ButtonGroup bgDirection = new ButtonGroup();
    bgDirection.add(myRbForward);
    bgDirection.add(myRbBackward);

    return directionPanel;
  }

  private JComponent createGlobalScopePanel() {
    JPanel scopePanel = new JPanel();
    scopePanel.setLayout(new GridBagLayout());
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.scope.group")));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 2;
    gbConstraints.weightx = 1;
    myRbProject = new JRadioButton(FindBundle.message("find.scope.whole.project.radio"), true);
    scopePanel.add(myRbProject, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    myRbModule = new JRadioButton(FindBundle.message("find.scope.module.radio"), false);
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
    myRbDirectory = new JRadioButton(FindBundle.message("find.scope.directory.radio"), false);
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
    myCbWithSubdirectories = new StateRestoringCheckBox(FindBundle.message("find.scope.directory.recursive.checkbox"), true);
    myCbWithSubdirectories.setSelected(true);
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
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.scope.group")));
    scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));

    myRbGlobal = new JRadioButton(FindBundle.message("find.scope.global.radio"), true);
    scopePanel.add(myRbGlobal);
    myRbSelectedText = new JRadioButton(FindBundle.message("find.scope.selected.text.radio"));
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
    originPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.origin.group")));
    originPanel.setLayout(new BoxLayout(originPanel, BoxLayout.Y_AXIS));

    myRbFromCursor = new JRadioButton(FindBundle.message("find.origin.from.cursor.radio"), true);
    originPanel.add(myRbFromCursor);
    myRbEntireScope = new JRadioButton(FindBundle.message("find.origin.entire.scope.radio"));
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

