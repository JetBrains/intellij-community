package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 25, 2004
 * Time: 1:52:18 PM
 * To change this template use File | Settings | File Templates.
 */
class EditVarConstraintsDialog extends DialogWrapper {
  private JTextField maxoccurs;
  private JCheckBox applyWithinTypeHierarchy;
  private JCheckBox notRegexp;
  private CompletionTextField regexp;
  private JTextField minoccurs;
  private JPanel mainForm;
  private JCheckBox notWrite;
  private JCheckBox notRead;
  private JCheckBox write;
  private JCheckBox read;
  private JList parameterList;
  private JCheckBox partOfSearchResults;
  private JCheckBox notExprType;
  private CompletionTextField regexprForExprType;
  private SearchModel model;
  private JCheckBox exprTypeWithinHierarchy;

  private List<Variable> variables;
  private Variable current;
  private JCheckBox wholeWordsOnly;
  private JCheckBox formalArgTypeWithinHierarchy;
  private JCheckBox invertFormalArgType;
  private CompletionTextField formalArgType;
  private JTextField customScriptCode;
  private JCheckBox maxoccursUnlimited;

  EditVarConstraintsDialog(Project project,SearchModel _model,List<Variable> _variables, boolean replaceContext, FileType fileType) {
    super(project,false);

    //regexp.getDocument().addDocumentListener(
    //  new DocumentAdapter() {
    //    public void documentChanged(DocumentEvent event) {
    //      doProcessing(applyWithinTypeHierarchy, regexp);
    //    }
    //  }
    //);
    
    regexprForExprType.getDocument().addDocumentListener(
      new DocumentAdapter() {
        public void documentChanged(DocumentEvent event) {
          doProcessing(exprTypeWithinHierarchy, regexprForExprType);
        }
      }
    );
    
    formalArgType.getDocument().addDocumentListener(
      new DocumentAdapter() {
        public void documentChanged(DocumentEvent event) {
          doProcessing(formalArgTypeWithinHierarchy, formalArgType);
        }
      }
    );
      
    variables = _variables;
    model = _model;

    setTitle(SSRBundle.message("editvarcontraints.edit.variables"));

    partOfSearchResults.setEnabled(!replaceContext);
    if (fileType == StdFileTypes.JAVA) {

      formalArgTypeWithinHierarchy.setEnabled(true);
      invertFormalArgType.setEnabled(true);
      formalArgType.setEnabled(true);

      exprTypeWithinHierarchy.setEnabled(true);
      notExprType.setEnabled(true);
      regexprForExprType.setEnabled(true);

      read.setEnabled(true);
      notRead.setEnabled(true);
      write.setEnabled(true);
      notWrite.setEnabled(true);

      applyWithinTypeHierarchy.setEnabled(true);
    } else {
      formalArgTypeWithinHierarchy.setEnabled(false);
      invertFormalArgType.setEnabled(false);
      formalArgType.setEnabled(false);

      exprTypeWithinHierarchy.setEnabled(false);
      notExprType.setEnabled(false);
      regexprForExprType.setEnabled(false);

      read.setEnabled(false);
      notRead.setEnabled(false);
      write.setEnabled(false);
      notWrite.setEnabled(false);

      applyWithinTypeHierarchy.setEnabled(false);
    }

    parameterList.setModel(
      new AbstractListModel() {
        public Object getElementAt(int index) {
          return variables.get(index);
        }

        public int getSize() {
          return variables.size();
        }
      }
    );

    parameterList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );

    parameterList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        boolean rollingBackSelection;

        public void valueChanged(ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) return;
          if (rollingBackSelection) {
            rollingBackSelection=false;
            return;
          }
          Variable var = variables.get(parameterList.getSelectedIndex());
          if (validateParameters()) {
            if (current!=null) copyValuesFromUI(current);
            copyValuesToUI(var);
            current = var;
          } else {
            rollingBackSelection = true;
            parameterList.setSelectedIndex(e.getFirstIndex()==parameterList.getSelectedIndex()?e.getLastIndex():e.getFirstIndex());
          }
        }
      }
    );

    parameterList.setCellRenderer(
      new DefaultListCellRenderer() {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          return super.getListCellRendererComponent(list, ((Variable)value).getName(), index, isSelected, cellHasFocus);    //To change body of overridden methods use File | Settings | File Templates.
        }
      }
    );

    maxoccursUnlimited.addChangeListener(
      new MyChangeListener(maxoccurs)
    );
    
    init();

    if (variables.size() > 0) parameterList.setSelectedIndex(0);
  }


  private static void doProcessing(JCheckBox checkBox, CompletionTextField textField) {
    checkBox.setEnabled( textField.getText().length() > 0);
    if (!checkBox.isEnabled()) checkBox.setSelected(false);
  }

  private boolean validateParameters() {
    return validateRegExp(regexp) && validateRegExp(regexprForExprType) &&
           validateIntOccurence(minoccurs) &&
           (maxoccursUnlimited.isSelected() || validateIntOccurence(maxoccurs));
  }

  protected JComponent createCenterPanel() {
    return mainForm;
  }

  protected void doOKAction() {
    if(validateParameters()) {
      if (current!=null) copyValuesFromUI(current);
      super.doOKAction();
    }
  }

  void copyValuesFromUI(Variable var) {
    MatchVariableConstraint varInfo = (var!=null)?model.getConfig().getMatchOptions().getVariableConstraint(var.getName()):null;

    if (varInfo == null) {
      varInfo = new MatchVariableConstraint();
      varInfo.setName(var.getName());
      model.getConfig().getMatchOptions().addVariableConstraint(varInfo);
    }

    varInfo.setInvertReadAccess(notRead.isSelected());
    varInfo.setReadAccess(read.isSelected());
    varInfo.setInvertWriteAccess(notWrite.isSelected());
    varInfo.setWriteAccess(write.isSelected());
    varInfo.setRegExp(regexp.getText());
    varInfo.setInvertRegExp(notRegexp.isSelected());

    int minCount = Integer.parseInt( minoccurs.getText() );
    varInfo.setMinCount(minCount);

    int maxCount;
    if (maxoccursUnlimited.isSelected()) maxCount = Integer.MAX_VALUE;
    else maxCount = Integer.parseInt( maxoccurs.getText() );

    varInfo.setMaxCount(maxCount);
    varInfo.setWithinHierarchy(applyWithinTypeHierarchy.isSelected());
    varInfo.setInvertRegExp(notRegexp.isSelected());

    varInfo.setPartOfSearchResults(partOfSearchResults.isEnabled() && partOfSearchResults.isSelected());

    varInfo.setInvertExprType(notExprType.isSelected());
    varInfo.setNameOfExprType(regexprForExprType.getText());
    varInfo.setExprTypeWithinHierarchy(exprTypeWithinHierarchy.isSelected());
    varInfo.setWholeWordsOnly(wholeWordsOnly.isSelected());
    varInfo.setInvertFormalType(invertFormalArgType.isSelected());
    varInfo.setFormalArgTypeWithinHierarchy(formalArgTypeWithinHierarchy.isSelected());
    varInfo.setNameOfFormalArgType(formalArgType.getText());
    varInfo.setScriptCodeConstraint("\"" + customScriptCode.getText() + "\"");
  }

  void copyValuesToUI(Variable var) {
    MatchVariableConstraint varInfo = (var!=null)?model.getConfig().getMatchOptions().getVariableConstraint(var.getName()):null;

    if (varInfo == null) {
      notRead.setSelected(false);
      notRegexp.setSelected(false);
      read.setSelected(false);
      notWrite.setSelected(false);
      write.setSelected(false);
      regexp.setText("");

      minoccurs.setText("1");
      maxoccurs.setText("1");
      maxoccursUnlimited.setSelected(false);
      applyWithinTypeHierarchy.setSelected(false);
      partOfSearchResults.setSelected(false);

      regexprForExprType.setText("");
      notExprType.setSelected(false);
      exprTypeWithinHierarchy.setSelected(false);
      wholeWordsOnly.setSelected(false);

      invertFormalArgType.setSelected(false);
      formalArgTypeWithinHierarchy.setSelected(false);
      formalArgType.setText("");
      customScriptCode.setText("");
    } else {
      notRead.setSelected(varInfo.isInvertReadAccess());
      read.setSelected(varInfo.isReadAccess());
      notWrite.setSelected(varInfo.isInvertWriteAccess());
      write.setSelected(varInfo.isWriteAccess());
      
      applyWithinTypeHierarchy.setSelected(varInfo.isWithinHierarchy());
      regexp.setText(varInfo.getRegExp());
      //doProcessing(applyWithinTypeHierarchy,regexp);
      
      notRegexp.setSelected(varInfo.isInvertRegExp());
      minoccurs.setText(Integer.toString(varInfo.getMinCount()));

      if(varInfo.getMaxCount() == Integer.MAX_VALUE) {
        maxoccursUnlimited.setSelected(true);
        maxoccurs.setText("");
      } else {
        maxoccursUnlimited.setSelected(false);
        maxoccurs.setText(Integer.toString(varInfo.getMaxCount()));
      }

      partOfSearchResults.setSelected( partOfSearchResults.isEnabled() && varInfo.isPartOfSearchResults() );

      exprTypeWithinHierarchy.setSelected( varInfo.isExprTypeWithinHierarchy() );
      regexprForExprType.setText( varInfo.getNameOfExprType() );
      doProcessing(exprTypeWithinHierarchy, regexprForExprType);
      
      notExprType.setSelected( varInfo.isInvertExprType() );
      wholeWordsOnly.setSelected( varInfo.isWholeWordsOnly() );

      invertFormalArgType.setSelected( varInfo.isInvertFormalType() );
      formalArgTypeWithinHierarchy.setSelected( varInfo.isFormalArgTypeWithinHierarchy() );
      formalArgType.setText( varInfo.getNameOfFormalArgType() );
      doProcessing(formalArgTypeWithinHierarchy,formalArgType);
      customScriptCode.setText( StringUtil.stripQuotesAroundValue(varInfo.getScriptCodeConstraint()) );
    }
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.EditVarConstraintsDialog";
  }

  private static boolean validateRegExp(CompletionTextField field) {
    try {
      if (field.getText().length() > 0) {
        Pattern.compile(field.getText());
      }
    } catch(PatternSyntaxException ex) {
      Messages.showErrorDialog(SSRBundle.message("invalid.regular.expression"), SSRBundle.message("invalid.regular.expression"));
      field.requestFocus();
      return false;
    }
    return true;
  }

  private static boolean validateIntOccurence(JTextField field) {
    try {
      int a = Integer.parseInt(field.getText());
      if (a==-1) throw new NumberFormatException();
    } catch(NumberFormatException ex) {
      Messages.showErrorDialog(SSRBundle.message("invalid.occurence.count"), SSRBundle.message("invalid.occurence.count"));
      field.requestFocus();
      return false;
    }
    return true;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.search.replace.structural.editvariable");
  }

  private class MyChangeListener implements ChangeListener {
    JTextField textField;

    MyChangeListener(JTextField _minoccurs) {
      textField = _minoccurs;
    }

    public void stateChanged(ChangeEvent e) {
      final JCheckBox jCheckBox = (JCheckBox)e.getSource();

      if (jCheckBox.isSelected()) {
        textField.setEnabled(false);
      }
      else {
        textField.setEnabled(true);
      }
    }
  }
}
