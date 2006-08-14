package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.ui.VisibilityPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

/**
 * @author dsl
 */
public abstract class MoveInstanceMethodDialogBase extends RefactoringDialog {
  protected final PsiMethod myMethod;
  protected final PsiVariable[] myVariables;

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  protected JList myList;
  protected VisibilityPanel myVisibilityPanel;
  protected String myRefactoringName;

  public MoveInstanceMethodDialogBase(PsiMethod method,
                                      PsiVariable[] variables,
                                      String refactoringName) {
    super(method.getProject(), true);
    myMethod = method;
    myVariables = variables;
    myRefactoringName = refactoringName;
    setTitle(myRefactoringName);
    init();
  }

  protected JPanel createListAndVisibilityPanels() {
    myList = createTargetVariableChooser();
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
    return hBox;
  }

  protected JList createTargetVariableChooser() {
    final JList list = new JList(new MyListModel());
    list.setCellRenderer(new MyListCellRenderer());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        getOKAction().setEnabled(!list.getSelectionModel().isSelectionEmpty());
      }
    });
    return list;
  }

  protected static VisibilityPanel createVisibilityPanel() {
    final VisibilityPanel visibilityPanel = new VisibilityPanel (false, false);
    visibilityPanel.setVisibility (null);
    return visibilityPanel;
  }

  protected boolean verifyTargetClass (PsiClass targetClass) {
    if (targetClass.isInterface()) {
      final Project project = getProject();
      if (targetClass.getManager().getSearchHelper().findInheritors(targetClass, targetClass.getUseScope(), false).length == 0) {
        final String message = RefactoringBundle.message("0.is.an.interface.that.has.no.implementing.classes", UsageViewUtil.getDescriptiveName(targetClass));

        Messages.showErrorDialog(project, message, myRefactoringName);
        return false;
      }

      final String message = RefactoringBundle.message("0.is.an.interface.method.implementation.will.be.added.to.all.directly.implementing.classes",
                                                       UsageViewUtil.getDescriptiveName(targetClass));

      final int result = Messages.showYesNoDialog(project, message, myRefactoringName,
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
