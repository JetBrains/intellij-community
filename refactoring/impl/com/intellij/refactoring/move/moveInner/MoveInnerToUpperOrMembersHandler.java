package com.intellij.refactoring.move.moveInner;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveMembers.MoveMembersHandler;
import com.intellij.refactoring.util.RadioUpDownListener;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MoveInnerToUpperOrMembersHandler extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    return element instanceof PsiClass && element.getParent() instanceof PsiClass &&
           ((PsiClass) element).hasModifierProperty(PsiModifier.STATIC) &&
           (targetContainer == null || targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass)elements[0], false)));
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog((PsiClass)elements[0], project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    MoveHandlerDelegate delegate = dialog.getRefactoringHandler();
    if (delegate != null) {
      delegate.doMove(project, elements, targetContainer, callback);
    }
  }

  public static class SelectInnerOrMembersRefactoringDialog extends DialogWrapper {
    private JRadioButton myRbMoveInner;
    private JRadioButton myRbMoveMembers;
    private String myClassName;

    public SelectInnerOrMembersRefactoringDialog(final PsiClass innerClass, Project project) {
      super(project, true);
      setTitle(RefactoringBundle.message("select.refactoring.title"));
      myClassName = innerClass.getName();
      init();
    }

    protected JComponent createNorthPanel() {
      return new JLabel(RefactoringBundle.message("what.would.you.like.to.do"));
    }

    public JComponent getPreferredFocusedComponent() {
      return myRbMoveInner;
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.refactoring.move.MoveHandler.SelectRefactoringDialog";
    }

    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      myRbMoveInner = new JRadioButton();
      myRbMoveInner.setText(RefactoringBundle.message("move.inner.class.to.upper.level", myClassName));
      myRbMoveInner.setSelected(true);
      myRbMoveMembers = new JRadioButton();
      myRbMoveMembers.setText(RefactoringBundle.message("move.inner.class.to.another.class", myClassName));


      ButtonGroup gr = new ButtonGroup();
      gr.add(myRbMoveInner);
      gr.add(myRbMoveMembers);

      new RadioUpDownListener(myRbMoveInner, myRbMoveMembers);

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbMoveInner);
      box.add(myRbMoveMembers);
      panel.add(box, BorderLayout.CENTER);
      return panel;
    }

    @Nullable
    public MoveHandlerDelegate getRefactoringHandler() {
      if (myRbMoveInner.isSelected()) {
        return new MoveInnerToUpperHandler();
      }
      if (myRbMoveMembers.isSelected()) {
        return new MoveMembersHandler();
      }
      return null;
    }
  }
}
