package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class MoveInstanceMethodDialog extends MoveInstanceMethodDialogBase {
  @NonNls private static final String KEY = "#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialog";

  //Map from classes referenced by 'this' to sets of referenced members
  private Map<PsiClass, Set<PsiMember>> myThisClassesMap;

  private Map<PsiClass, EditorTextField> myOldClassParameterNameFields;

  public MoveInstanceMethodDialog(final PsiMethod method,
                                  final PsiVariable[] variables) {
    super(method, variables, MoveInstanceMethodHandler.REFACTORING_NAME);
  }

  protected String getDimensionServiceKey() {
    return KEY;
  }

  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new GridBagLayout());
    final JLabel jLabel = new JLabel();
    mainPanel.add(jLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));

    myList = createTargetVariableChooser();
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        validateTextFields(e.getFirstIndex());
      }
    });

    jLabel.setText(RefactoringBundle.message("moveInstanceMethod.select.an.instance.parameter"));

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    mainPanel.add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));

    myVisibilityPanel = createVisibilityPanel();
    mainPanel.add(myVisibilityPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new Insets(0,0,0,0), 0,0));

    final JPanel parametersPanel = createParametersPanel();
    if (parametersPanel != null) {
      mainPanel.add(parametersPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0));
    }

    jLabel.setLabelFor(myList);
    validateTextFields(myList.getSelectedIndex());
    return mainPanel;
  }

  private void validateTextFields(final int selectedIndex) {
    for (EditorTextField textField : myOldClassParameterNameFields.values()) {
      textField.setEnabled(true);
    }

    final PsiVariable variable = myVariables[selectedIndex];
    if (variable instanceof PsiField) {
      final PsiField field = (PsiField)variable;
      final PsiClass hisClass = field.getContainingClass();
      final Set<PsiMember> members = myThisClassesMap.get(hisClass);
      if (members != null && members.size() == 1 && members.contains(field)) {  //Just the field is referenced
        myOldClassParameterNameFields.get(hisClass).setEnabled(false);
      }
    }
  }

  @Nullable
  private JPanel createParametersPanel () {
    myThisClassesMap = MoveInstanceMembersUtil.getThisClassesToMembers(myMethod);
    myOldClassParameterNameFields = new HashMap<PsiClass, EditorTextField>();
    if (myThisClassesMap.size() == 0) return null;
    JPanel panel = new JPanel(new VerticalFlowLayout());
    for (PsiClass aClass : myThisClassesMap.keySet()) {
      final String text = RefactoringBundle.message("move.method.this.parameter.label", aClass.getName());
      panel.add(new JLabel(text));

      String suggestedName = MoveInstanceMethodHandler.suggestParameterNameForThisClass(aClass);
      final EditorTextField field = new EditorTextField(suggestedName, getProject(), StdFileTypes.JAVA);
      field.setMinimumSize(new Dimension(field.getPreferredSize()));
      myOldClassParameterNameFields.put(aClass, field);
      panel.add(field);
    }
    return panel;
  }

  protected void doAction() {
    Map<PsiClass, String> parameterNames = new LinkedHashMap<PsiClass, String>();
    final Iterator<PsiClass>       classesIterator = myThisClassesMap.keySet().iterator();
    for (; classesIterator.hasNext();) {
      final PsiClass aClass = classesIterator.next();
      EditorTextField field = myOldClassParameterNameFields.get(aClass);
      if (field.isEnabled()) {
        String parameterName = field.getText().trim();
        if (!myMethod.getManager().getNameHelper().isIdentifier(parameterName)) {
          Messages.showErrorDialog(getProject(), RefactoringBundle.message("move.method.enter.a.valid.name.for.parameter"), myRefactoringName);
          return;
        }
        parameterNames.put(aClass, parameterName);
      }
    }

    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    if (targetVariable == null) return;
    final MoveInstanceMethodProcessor processor = new MoveInstanceMethodProcessor(myMethod.getProject(),
                                                                                  myMethod, targetVariable,
                                                                                  myVisibilityPanel.getVisibility(),
                                                                                  parameterNames);
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MOVE_INSTANCE_METHOD);
  }
}
