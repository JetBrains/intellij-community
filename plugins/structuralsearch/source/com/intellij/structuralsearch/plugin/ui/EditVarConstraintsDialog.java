package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.structuralsearch.MatchVariableConstraint;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
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
  private JCheckBox minoccursUnlimited;

  EditVarConstraintsDialog(Project project,SearchModel _model,List<Variable> _variables, boolean replaceContext, FileType fileType) {
    super(project,false);

    variables = _variables;
    model = _model;

    setTitle("Edit Variables");

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

    minoccursUnlimited.addChangeListener(
      new MyChangeListener(minoccurs)
    );

    maxoccursUnlimited.addChangeListener(
      new MyChangeListener(maxoccurs)
    );

    init();

    if (variables.size()>0) parameterList.setSelectedIndex(0);
  }

  private boolean validateParameters() {
    return validateRegExp(regexp) && validateRegExp(regexprForExprType) &&
           (minoccursUnlimited.isSelected() || validateIntOccurence(minoccurs)) &&
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

    int minCount;
    if (minoccursUnlimited.isSelected()) minCount = Integer.MAX_VALUE;
    else minCount = Integer.parseInt( minoccurs.getText() );
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
    varInfo.setScriptCodeConstraint(customScriptCode.getText());
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
      minoccursUnlimited.setSelected(false);
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
      
      regexp.setText(varInfo.getRegExp());
      notRegexp.setSelected(varInfo.isInvertRegExp());

      if(varInfo.getMinCount() == Integer.MAX_VALUE) {
        minoccursUnlimited.setSelected(true);
        minoccurs.setText("");
      } else {
        minoccursUnlimited.setSelected(false);
        minoccurs.setText(Integer.toString(varInfo.getMinCount()));
      }

      if(varInfo.getMaxCount() == Integer.MAX_VALUE) {
        maxoccursUnlimited.setSelected(true);
        maxoccurs.setText("");
      } else {
        maxoccursUnlimited.setSelected(false);
        maxoccurs.setText(Integer.toString(varInfo.getMaxCount()));
      }

      applyWithinTypeHierarchy.setSelected(varInfo.isWithinHierarchy());
      notRegexp.setSelected( varInfo.isInvertRegExp() );

      partOfSearchResults.setSelected( partOfSearchResults.isEnabled() && varInfo.isPartOfSearchResults() );

      regexprForExprType.setText( varInfo.getNameOfExprType() );
      notExprType.setSelected( varInfo.isInvertExprType() );
      exprTypeWithinHierarchy.setSelected( varInfo.isExprTypeWithinHierarchy() );
      wholeWordsOnly.setSelected( varInfo.isWholeWordsOnly() );

      invertFormalArgType.setSelected( varInfo.isInvertFormalType() );
      formalArgTypeWithinHierarchy.setSelected( varInfo.isFormalArgTypeWithinHierarchy() );
      formalArgType.setText( varInfo.getNameOfFormalArgType() );
      customScriptCode.setText( varInfo.getScriptCodeConstraint() );
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
      Messages.showErrorDialog("Invalid regular expression", "Invalid regular expression");
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
      Messages.showErrorDialog("Invalid occurence count", "Invalid occurence count");
      field.requestFocus();
      return false;
    }
    return true;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("find.structuredSearch");
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
