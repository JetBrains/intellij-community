package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author ven
 */
public class MoveInstanceMethodDialog extends MoveInstanceMethodDialogBase {
  private static final String KEY = "#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialog";
  private ArrayList<PsiClass> myRefThisClasses;
  private ArrayList<EditorTextField> myOldClassParameterNameFields;

  public MoveInstanceMethodDialog(final PsiMethod method,
                                  final PsiVariable[] variables) {
    super(method, variables, MoveInstanceMethodHandler.REFACTORING_NAME);
  }

  protected String getDimensionServiceKey() {
    return KEY;
  }

  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new GridBagLayout());
    final JLabel jLabel = new JLabel("Select an instance variable:");
    jLabel.setDisplayedMnemonic('i');
    mainPanel.add(jLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));

    myList = createTargetVariableChooser();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    mainPanel.add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));

    myVisibilityPanel = createVisibilityPanel();
    mainPanel.add(myVisibilityPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new Insets(0,0,0,0), 0,0));

    final JPanel parametersPanel = createParametersPanel();
    if (parametersPanel != null) {
      mainPanel.add(parametersPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0));
    }

    jLabel.setLabelFor(myList);
    return mainPanel;
  }

  private JPanel createParametersPanel () {
    myRefThisClasses = new ArrayList<PsiClass>();
    myOldClassParameterNameFields = new ArrayList<EditorTextField>();
    final PsiClass[] classes = MoveMethodUtil.getThisClassesNeeded(myMethod);
    if (classes.length == 0) return null;
    JPanel panel = new JPanel(new VerticalFlowLayout());
    for (int i = 0; i < classes.length; i++) {
      PsiClass aClass = classes[i];
      final String text = "Select a name for '" + aClass.getName() + ".this' parameter";
      final JLabel jLabel1 = new JLabel(text);
      panel.add(jLabel1);

      String suggestedName = MoveInstanceMethodHandler.suggestParameterNameForThisClass(aClass);
      final EditorTextField field = new EditorTextField(suggestedName, getProject(), StdFileTypes.JAVA);
      myOldClassParameterNameFields.add(field);
      myRefThisClasses.add(aClass);
      panel.add(field);
    }
    return panel;
  }

  protected void doAction() {

    Map<PsiClass, String> parameterNames = new LinkedHashMap<PsiClass, String>();
    final Iterator<EditorTextField> fieldsIterator = myOldClassParameterNameFields.iterator();
    final Iterator<PsiClass>       classesIterator = myRefThisClasses.iterator();
    for (; fieldsIterator.hasNext();) {
      EditorTextField field = fieldsIterator.next();
      final PsiClass aClass = classesIterator.next();
      String parameterName = field.getText().trim();
      if (!myMethod.getManager().getNameHelper().isIdentifier(parameterName)) {
        Messages.showErrorDialog(getProject(), "Please Enter a Valid name for Parameter", myRefactoringName);
        return;
      }
      parameterNames.put(aClass, parameterName);
    }

    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    final MoveInstanceMethodProcessor processor = new MoveInstanceMethodProcessor(myMethod.getProject(),
                                                                                            myMethod, targetVariable,
                                                                                            myVisibilityPanel.getVisibility(),
                                                                                  parameterNames);
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

}
