package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class EditTemplateDialog extends DialogWrapper {
  private TemplateImpl[] myTemplates;
  private TemplateImpl myTemplate;

  private final JTextField myKeyField;
  private final JTextField myDescription;
  private final ComboBox myGroupCombo;
  private final Editor myTemplateEditor;
  private ArrayList myVariables = new ArrayList();

  private JComboBox myExpandByCombo;
  private String myDefaultShortcutItem;
  private JCheckBox myCbReformat;
  private JCheckBox myCbShortenFQNames;

  private JCheckBox myCbJavaCode;
  private JCheckBox myCbJavaComment;
  private JCheckBox myCbJavaString;
  private JCheckBox myCbCompletion;
  private JCheckBox myCbOther;
  private JCheckBox myCbHTML;
  private JCheckBox myCbXML;
  private JCheckBox myCbJSP;

  private JButton myEditVariablesButton;

  private static final String SPACE = "Space";
  private static final String TAB = "Tab";
  private static final String ENTER = "Enter";

  public EditTemplateDialog(Component parent, String title, TemplateImpl template, TemplateImpl[] templates, String defaultShortcut) {
    super(parent, true);
    setOKButtonText("OK");
    setTitle(title);

    myTemplate = template;
    myTemplates = templates;
    myDefaultShortcutItem = "Default (" + defaultShortcut + ")";

    myKeyField=new JTextField();
    myDescription=new JTextField();
    myGroupCombo=new ComboBox(-1);
    myTemplateEditor = TemplateEditorUtil.createEditor(false);

    init();
    reset();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.templates.defineTemplates.editTemplate");
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.codeInsight.template.impl.EditTemplateDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myKeyField;
  }

  protected void dispose() {
    super.dispose();
    EditorFactory.getInstance().releaseEditor(myTemplateEditor);
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weighty = 0;

    gbConstraints.gridwidth = 2;
    gbConstraints.gridx = 0;

    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder("Template text"));
    JPanel textPanel = panel1;
    textPanel.setPreferredSize(new Dimension(500, 160));
    textPanel.setMinimumSize(new Dimension(500, 160));
    textPanel.setLayout(new BorderLayout());
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.gridy++;
    textPanel.add(myTemplateEditor.getComponent(), BorderLayout.CENTER);
    panel.add(textPanel, gbConstraints);

    gbConstraints.weighty = 0;
    gbConstraints.gridy++;
    myEditVariablesButton = new JButton("Edit variables");
    myEditVariablesButton.setDefaultCapable(false);
    myEditVariablesButton.setMaximumSize(myEditVariablesButton.getPreferredSize());
    panel.add(myEditVariablesButton, gbConstraints);

    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridy++;
    panel.add(createTemplateOptionsPanel(), gbConstraints);

    gbConstraints.gridx = 1;
    panel.add(createContextPanel(), gbConstraints);

    myKeyField.getDocument().addDocumentListener(new com.intellij.ui.DocumentAdapter() {
      protected void textChanged(javax.swing.event.DocumentEvent e) {
        validateOKButton();
      }
    });

    myTemplateEditor.getDocument().addDocumentListener(
      new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          validateOKButton();
          validateEditVariablesButton();
        }
      }
    );

    myEditVariablesButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          editVariables();
        }
      }
    );
    return panel;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.insets = new Insets(4,4,4,4);
    gbConstraints.weighty = 1;

    gbConstraints.weightx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    JLabel keyPrompt = new JLabel("Abbreviation:");
    keyPrompt.setDisplayedMnemonic('A');
    keyPrompt.setLabelFor(myKeyField);
    panel.add(keyPrompt, gbConstraints);

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    panel.add(myKeyField, gbConstraints);

    gbConstraints.weightx = 0;
    gbConstraints.gridx = 2;
    JLabel groupPrompt = new JLabel("Group:");
    groupPrompt.setDisplayedMnemonic('G');
    groupPrompt.setLabelFor(myGroupCombo);
    panel.add(groupPrompt, gbConstraints);

    gbConstraints.weightx = 1;
    gbConstraints.gridx = 3;
    myGroupCombo.setEditable(true);
    panel.add(myGroupCombo, gbConstraints);

    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    JLabel descriptionPrompt = new JLabel("Description:");
    descriptionPrompt.setDisplayedMnemonic('D');
    descriptionPrompt.setLabelFor(myDescription);
    panel.add(descriptionPrompt, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.gridwidth = 3;
    gbConstraints.weightx = 1;
    panel.add(myDescription, gbConstraints);

    return panel;
  }

  private JPanel createTemplateOptionsPanel() {
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder("Options"));
    JPanel panel = panel1;
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.weighty = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridy = 0;
    panel.add(new JLabel("Expand with       "), gbConstraints);

    gbConstraints.gridx = 1;
    myExpandByCombo = new JComboBox();
    myExpandByCombo.addItem(myDefaultShortcutItem);
    myExpandByCombo.addItem(SPACE);
    myExpandByCombo.addItem(TAB);
    myExpandByCombo.addItem(ENTER);
    panel.add(myExpandByCombo, gbConstraints);
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 2;
    panel.add(new JPanel(), gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.gridwidth = 3;
    myCbReformat = new JCheckBox("Reformat according to style");
    myCbReformat.setMnemonic('R');
    panel.add(myCbReformat, gbConstraints);

    gbConstraints.gridy++;
    myCbShortenFQNames = new JCheckBox("Shorten FQ names");
    myCbShortenFQNames.setMnemonic('F');
    panel.add(myCbShortenFQNames, gbConstraints);

    gbConstraints.weighty = 1;
    gbConstraints.gridy++;
    panel.add(new JPanel(), gbConstraints);

    return panel;
  }

  private JPanel createContextPanel() {
    ChangeListener listener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myExpandByCombo.setEnabled(!isEnabledInStaticContextOnly());
      }

    };

    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder("Context"));
    JPanel panel = panel1;
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;

    gbConstraints.gridy = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    myCbJavaCode = new JCheckBox("Java code");
    myCbJavaCode.setMnemonic('J');
    myCbJavaCode.getModel().addChangeListener(listener);
    panel.add(myCbJavaCode, gbConstraints);

    gbConstraints.gridx = 1;
    myCbHTML = new JCheckBox("HTML");
    myCbHTML.setMnemonic('H');
    panel.add(myCbHTML, gbConstraints);
    myCbHTML.getModel().addChangeListener(listener);

    gbConstraints.gridy++;
    gbConstraints.gridx = 0;
    myCbJavaComment = new JCheckBox("Java comment");
    myCbJavaComment.setMnemonic('c');
    panel.add(myCbJavaComment, gbConstraints);
    myCbJavaComment.getModel().addChangeListener(listener);

    gbConstraints.gridx = 1;
    myCbXML = new JCheckBox("XML");
    myCbXML.setMnemonic('x');
    panel.add(myCbXML, gbConstraints);
    myCbXML.getModel().addChangeListener(listener);

    gbConstraints.gridy++;
    gbConstraints.gridx = 0;
    myCbJavaString = new JCheckBox("Java string");
    myCbJavaString.setMnemonic('s');
    panel.add(myCbJavaString, gbConstraints);
    myCbJavaString.getModel().addChangeListener(listener);

    gbConstraints.gridx = 1;
    myCbJSP = new JCheckBox("JSP");
    myCbJSP.setMnemonic('P');
    panel.add(myCbJSP, gbConstraints);
    myCbJSP.getModel().addChangeListener(listener);

    gbConstraints.gridy++;
    gbConstraints.gridx = 0;
    myCbCompletion = new JCheckBox("Smart type completion");
    myCbCompletion.setMnemonic('o');
    panel.add(myCbCompletion, gbConstraints);
    myCbCompletion.getModel().addChangeListener(listener);

    gbConstraints.gridx = 1;
    myCbOther = new JCheckBox("Other");
    myCbOther.setMnemonic('t');
    panel.add(myCbOther, gbConstraints);
    myCbOther.getModel().addChangeListener(listener);

    addUpdateHighlighterAction(myCbJavaCode);
    addUpdateHighlighterAction(myCbJavaComment);
    addUpdateHighlighterAction(myCbJavaString);
    addUpdateHighlighterAction(myCbCompletion);
    addUpdateHighlighterAction(myCbXML);
    addUpdateHighlighterAction(myCbHTML);
    addUpdateHighlighterAction(myCbJSP);
    addUpdateHighlighterAction(myCbOther);

    return panel;
  }

  private boolean isEnabledInStaticContextOnly() {
    return
        myCbCompletion.isSelected() &&
        !myCbJavaCode.isSelected() &&
        !myCbJavaComment.isSelected() &&
        !myCbJavaString.isSelected() &&
        !myCbXML.isSelected() &&
        !myCbHTML.isSelected() &&
        !myCbJSP.isSelected() &&
        !myCbOther.isSelected();
  }

  private void addUpdateHighlighterAction(JCheckBox checkbox) {
    checkbox.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          updateHighlighter();
        }
      }
    );
  }

  private void updateHighlighter() {
    TemplateContext templateContext = new TemplateContext();
    updateTemplateContext(templateContext);
    TemplateEditorUtil.setHighlighter(myTemplateEditor, templateContext);
    ((EditorEx) myTemplateEditor).repaint(0, myTemplateEditor.getDocument().getTextLength());
  }

  private void validateEditVariablesButton() {
    ArrayList variables = new ArrayList();
    parseVariables(myTemplateEditor.getDocument().getCharsSequence(), variables);

    boolean enable = false;

    for (Iterator iterator = variables.iterator(); iterator.hasNext();) {
      Variable variable = (Variable)iterator.next();
      if (!TemplateImpl.INTERNAL_VARS_SET.contains(variable.getName())) enable = true;
    }

    myEditVariablesButton.setEnabled(enable);
  }

  private void validateOKButton() {
    boolean isEnabled = true;
    if(myKeyField.getText().trim().length() == 0) {
      isEnabled = false;
    }
    if(myTemplateEditor.getDocument().getTextLength() == 0) {
      isEnabled = false;
    }
    setOKActionEnabled(isEnabled);
  }

  private void reset() {
    myKeyField.setText(myTemplate.getKey());
    myDescription.setText(myTemplate.getDescription());

    if(myTemplate.getShortcutChar() == TemplateSettings.DEFAULT_CHAR) {
      myExpandByCombo.setSelectedItem(myDefaultShortcutItem);
    }
    else if(myTemplate.getShortcutChar() == TemplateSettings.TAB_CHAR) {
      myExpandByCombo.setSelectedItem(TAB);
    }
    else if(myTemplate.getShortcutChar() == TemplateSettings.ENTER_CHAR) {
      myExpandByCombo.setSelectedItem(ENTER);
    }
    else {
      myExpandByCombo.setSelectedItem(SPACE);
    }

    CommandProcessor.getInstance().executeCommand(
        null, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              myTemplateEditor.getDocument().replaceString(0, myTemplateEditor.getDocument().getTextLength(), myTemplate.getString());
            }
          });
        }
      },
      "",
      null
    );

    UndoManager.getGlobalInstance().clearUndoRedoQueue(TextEditorProvider.getInstance().getTextEditor(myTemplateEditor));

    Set groups = new TreeSet();
    for (int i = 0; i < myTemplates.length; i++) {
      TemplateImpl template = myTemplates[i];
      groups.add(template.getGroupName());
    }

    for (Iterator i = groups.iterator(); i.hasNext();) {
      String groupName = (String)i.next();
      myGroupCombo.addItem(groupName);
    }

    myGroupCombo.setSelectedItem(myTemplate.getGroupName());

    myVariables.clear();
    for(int i = 0; i < myTemplate.getVariableCount(); i++) {
      Variable variable = new Variable(myTemplate.getVariableNameAt(i),
                                       myTemplate.getExpressionStringAt(i),
                                       myTemplate.getDefaultValueStringAt(i),
                                       myTemplate.isAlwaysStopAt(i));
      myVariables.add(variable);
    }

    myCbJavaCode.setSelected(myTemplate.getTemplateContext().JAVA_CODE);
    myCbJavaComment.setSelected(myTemplate.getTemplateContext().JAVA_COMMENT);
    myCbJavaString.setSelected(myTemplate.getTemplateContext().JAVA_STRING);
    myCbCompletion.setSelected(myTemplate.getTemplateContext().COMPLETION);
    myCbOther.setSelected(myTemplate.getTemplateContext().OTHER);
    myCbHTML.setSelected(myTemplate.getTemplateContext().HTML);
    myCbXML.setSelected(myTemplate.getTemplateContext().XML);
    myCbJSP.setSelected(myTemplate.getTemplateContext().JSP);

    myCbReformat.setSelected(myTemplate.isToReformat());
    myCbShortenFQNames.setSelected(myTemplate.isToShortenLongNames());
    myExpandByCombo.setEnabled(!isEnabledInStaticContextOnly());

    updateHighlighter();
    validateOKButton();
    validateEditVariablesButton();
  }

  public void apply() {
    updateVariablesByTemplateText();
    myTemplate.setKey(myKeyField.getText().trim());
    myTemplate.setDescription(myDescription.getText().trim());
    myTemplate.setGroupName(((String)myGroupCombo.getSelectedItem()).trim());

    Object selectedItem = myExpandByCombo.getSelectedItem();
    if(myDefaultShortcutItem.equals(selectedItem)) {
      myTemplate.setShortcutChar(TemplateSettings.DEFAULT_CHAR);
    }
    else if(TAB.equals(selectedItem)) {
      myTemplate.setShortcutChar(TemplateSettings.TAB_CHAR);
    }
    else if(ENTER.equals(selectedItem)) {
      myTemplate.setShortcutChar(TemplateSettings.ENTER_CHAR);
    }
    else {
      myTemplate.setShortcutChar(TemplateSettings.SPACE_CHAR);
    }

    myTemplate.removeAllParsed();

    for(int i = 0; i < myVariables.size(); i++) {
      Variable variable = (Variable)myVariables.get(i);
      myTemplate.addVariable(variable.getName(),
                           variable.getExpressionString(),
                           variable.getDefaultValueString(),
                           variable.isAlwaysStopAt());
    }

    updateTemplateContext(myTemplate.getTemplateContext());

    myTemplate.setToReformat(myCbReformat.isSelected());
    myTemplate.setToShortenLongNames(myCbShortenFQNames.isSelected());

    myTemplate.setString(myTemplateEditor.getDocument().getText());
    myTemplate.parseSegments();
  }

  private void updateTemplateContext(TemplateContext templateContext) {
    templateContext.JAVA_CODE = myCbJavaCode.isSelected();
    templateContext.JAVA_COMMENT = myCbJavaComment.isSelected();
    templateContext.JAVA_STRING = myCbJavaString.isSelected();
    templateContext.COMPLETION = myCbCompletion.isSelected();
    templateContext.OTHER = myCbOther.isSelected();
    templateContext.HTML = myCbHTML.isSelected();
    templateContext.XML = myCbXML.isSelected();
    templateContext.JSP = myCbJSP.isSelected();
  }

  private void editVariables() {
    updateVariablesByTemplateText();
    ArrayList newVariables = new ArrayList();

    for(int i = 0; i < myVariables.size(); i++){
      Variable variable = (Variable)myVariables.get(i);
      if (!TemplateImpl.INTERNAL_VARS_SET.contains(variable.getName())) {
        newVariables.add(variable.clone());
      }
    }

    EditVariableDialog editVariableDialog = new EditVariableDialog(myTemplateEditor, myEditVariablesButton, newVariables);
    editVariableDialog.show();
    if(!editVariableDialog.isOK()) return;
    myVariables = newVariables;
  }

  private void updateVariablesByTemplateText() {

    ArrayList parsedVariables = new ArrayList();
    parseVariables(myTemplateEditor.getDocument().getCharsSequence(), parsedVariables);

    Hashtable oldVariableNames = new Hashtable();
    for(int j = 0; j < myVariables.size(); j++){
      Variable oldVariable = (Variable)myVariables.get(j);
      String name = oldVariable.getName();
      oldVariableNames.put(name, name);
    }

    Hashtable newVariableNames = new Hashtable();
    for(int j = 0; j < parsedVariables.size(); j++){
      Variable newVariable = (Variable)parsedVariables.get(j);
      String name = newVariable.getName();
      newVariableNames.put(name, name);
    }

    int oldVariableNumber = 0;
    for(int i = 0; i < parsedVariables.size(); i++){
      Variable variable = (Variable)parsedVariables.get(i);
      String name = variable.getName();
      if(oldVariableNames.get(name) != null) {
        Variable oldVariable = null;
        for(;oldVariableNumber<myVariables.size(); oldVariableNumber++) {
          oldVariable = (Variable)myVariables.get(oldVariableNumber);
          if(newVariableNames.get(oldVariable.getName()) != null) {
            break;
          }
          oldVariable = null;
        }
        oldVariableNumber++;
        if(oldVariable != null) {
          parsedVariables.set(i, oldVariable);
        }
      }
    }

    myVariables = parsedVariables;
  }

  public static void parseVariables(CharSequence text, ArrayList variables) {
    TemplateImplUtil.parseVariables(
      text,
      variables,
      TemplateImpl.INTERNAL_VARS_SET
    );
  }

  protected void doOKAction() {
    String key = myKeyField.getText().trim();
    for(int i = 0; i < myTemplates.length; i++) {
      TemplateImpl template = myTemplates[i];
      if(template.getKey().equals(key) && myTemplate != template) {
        Messages.showMessageDialog (
          getContentPane(),
          "Cannot save the template.\nTemplate with the abbreviation \"" + key + "\"\n" +
            "already exists in group \"" + template.getGroupName() + "\".\n" +
            "Try another abbreviation.",
          "Cannot Save",
          Messages.getErrorIcon()
        );
        return;
      }
    }

    if (!TemplateImplUtil.validateTemplateText(myTemplateEditor.getDocument().getText())) {
      Messages.showMessageDialog (
          getContentPane(),
          "Error parsing the template",
          "Cannot Save",
          Messages.getErrorIcon()
      );
      return;
    }

    super.doOKAction();
  }

}

