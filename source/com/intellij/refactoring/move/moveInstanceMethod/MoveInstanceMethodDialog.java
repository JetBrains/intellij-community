package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.ui.BaseRefactoringDialog;
import com.intellij.refactoring.ui.VisibilityPanel;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

/**
 * @author dsl
 */
public class MoveInstanceMethodDialog extends BaseRefactoringDialog {
  protected final PsiMethod myMethod;
  private final PsiVariable[] myVariables;
  private final String myRefactoringName;
  protected JList myList;

  protected VisibilityPanel myVisibilityPanel;

  public MoveInstanceMethodDialog(final PsiMethod method,
                              final PsiVariable[] variables,
                              String refactoringName) {
    super(method.getProject(), true);
    myMethod = method;
    myVariables = variables;
    myRefactoringName = refactoringName;
    setTitle(myRefactoringName);
    init();
  }

  protected JComponent createCenterPanel() {
    final Box vBox = Box.createVerticalBox();
    final Box labelBox = Box.createHorizontalBox();
    final JLabel jLabel = new JLabel("Select an instance parameter:");
    jLabel.setDisplayedMnemonic('i');
    labelBox.add(jLabel);
    labelBox.add(Box.createHorizontalGlue());
    vBox.add(labelBox);
    vBox.add(Box.createVerticalStrut(4));
    myList = new JList(new MyListModel());
    myList.setCellRenderer(new MyListCellRenderer());
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setSelectedIndex(0);
    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        getOKAction().setEnabled(!myList.getSelectionModel().isSelectionEmpty());
      }
    });
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    final JPanel hBox = new JPanel(new GridBagLayout());
    final GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridheight = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.insets = new Insets(0, 0, 0, 0);
    hBox.add(scrollPane, gbConstraints);
    hBox.add(Box.createHorizontalStrut(4));
    gbConstraints.weightx = 0;
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.gridx++;
    myVisibilityPanel = createVisibilityPanel ();
    hBox.add (myVisibilityPanel, gbConstraints);

    vBox.add(hBox);
    return vBox;
  }

  private VisibilityPanel createVisibilityPanel() {
    final VisibilityPanel visibilityPanel = new VisibilityPanel (false);
    visibilityPanel.setVisibility (VisibilityUtil.getVisibilityModifier (myMethod.getModifierList()));
    return visibilityPanel;
  }
  
  protected void doAction() {
    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    final MoveInstanceMethodProcessor processor = new MoveInstanceMethodProcessor(myMethod.getProject(),
                                                                                            myMethod, targetVariable,
                                                                                            myVisibilityPanel.getVisibility());
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

  protected boolean verifyTargetClass (PsiClass targetClass) {
    if (targetClass.isInterface()) {
      final String message = UsageViewUtil.getDescriptiveName(targetClass) + " is an interface. \n" +
                       "Method implementation will be added to all directly implementing classes.\n Proceed?";

      final int result = Messages.showYesNoDialog(myProject, message, myRefactoringName,
                                     Messages.getQuestionIcon());
      if (result != 0) return false;
    }

    return true;
  }

  private class MyListModel extends AbstractListModel {
    public int getSize() {
      return myVariables.length;
    }

    public Object getElementAt(int index) {
      return myVariables[index];
    }
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      final PsiVariable psiVariable = (PsiVariable)value;
      final String text = PsiFormatUtil.formatVariable(psiVariable,
                                           PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE,
                                           PsiSubstitutor.EMPTY);
      setIcon(psiVariable.getIcon(0));
      setText(text);
      return this;
    }
  }
}
