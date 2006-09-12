/**
 * created at Nov 26, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.NotNull;

public class MoveHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.MoveHandler");

  public static final int NOT_SUPPORTED = 0;
  public static final int CLASSES = 1;
  public static final int PACKAGES = 2;
  public static final int MEMBERS = 3;
  public static final int INNER_TO_UPPER = 4;
  public static final int INNER_TO_UPPER_OR_MEMBERS = 5;
  public static final int FILES = 6;
  public static final int DIRECTORIES = 7;
  public static final int MOVE_OR_REARRANGE_PACKAGE = 8;
  public static final int INSTANCE_METHOD = 9;
  public static final String REFACTORING_NAME = RefactoringBundle.message("move.tltle");


  public static interface TargetContainerFinder {
    PsiElement getTargetContainer(DataContext dataContext);
  }

  TargetContainerFinder myTargetContainerFinder;

  public MoveHandler(TargetContainerFinder finder) {
    myTargetContainerFinder = finder;
  }

  /**
   * called by an Action in AtomicAction when refactoring is invoked from Editor
   */
  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while(true){

      if (element == null) {
        if (file instanceof PsiPlainTextFile) {
          PsiElement[] elements = new PsiElement[]{file};
          if (MoveFilesOrDirectoriesUtil.canMoveFiles(elements)) {
            doMove(project, elements, null, null);
          }
          return;
        }

        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.at.the.class.method.or.field.to.be.refactored"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
        return;
      }

      if (tryToMoveElement(element, project, dataContext)) {
        return;
      } else {
        final TextRange range = element.getTextRange();
        if (range != null) {
          int relative = offset - range.getStartOffset();
          final PsiReference reference = element.findReferenceAt(relative);
          if (reference != null &&
              !(reference instanceof PsiJavaCodeReferenceElement &&
                ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnonymousClass)) {
            final PsiElement refElement = reference.resolve();
            if (refElement != null && tryToMoveElement(refElement, project, dataContext)) return;
          }
        }
      }

      element = element.getParent();
    }
  }

  private boolean tryToMoveElement(final PsiElement element, final Project project, final DataContext dataContext) {
    if ((element instanceof PsiFile && ((PsiFile)element).getVirtualFile() != null)
        || element instanceof PsiDirectory) {
      final PsiDirectory targetContainer = (PsiDirectory)myTargetContainerFinder.getTargetContainer(dataContext);
      MoveFilesOrDirectoriesUtil.doMove(project, new PsiElement[]{element}, targetContainer, null);
      return true;
    } else if (element instanceof PsiField) {
      MoveMembersImpl.doMove(project, new PsiElement[]{element}, null, null);
      return true;
    } else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        new MoveInstanceMethodHandler().invoke(project, new PsiElement[]{method}, dataContext);
      }
      else {
        MoveMembersImpl.doMove(project, new PsiElement[]{method}, null, null);
      }
      return true;
    } else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      if (aClass.getContainingClass() != null) { // this is inner class
        FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.move.moveInner");
        if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
          MoveInnerImpl.doMove(project, new PsiElement[]{aClass}, null);
        }
        else {
          SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog(aClass, project);
          dialog.show();
          if (dialog.isOK()) {
            int type = dialog.getRefactoringType();
            if (type == INNER_TO_UPPER) {
              MoveInnerImpl.doMove(project, new PsiElement[]{aClass}, null);
            }
            else if (type == MEMBERS) {
              MoveMembersImpl.doMove(project, new PsiElement[]{aClass}, null, null);
            }
          }
        }
        return true;
      }
      if (!(element instanceof PsiAnonymousClass)) {
        MoveClassesOrPackagesImpl.doMove(project, new PsiElement[]{aClass}, myTargetContainerFinder.getTargetContainer(dataContext), null);
      }
      else {
        new AnonymousToInnerHandler().invoke(project, (PsiAnonymousClass)element);
      }

      return true;
    }

    return false;
  }

  /**
   * called by an Action in AtomicAction
   */
  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    doMove(project, elements, dataContext != null ? myTargetContainerFinder.getTargetContainer(dataContext) : null, null);
  }

  /**
   * must be invoked in AtomicAction
   */
  public static void doMove(Project project, @NotNull PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    if (elements.length == 0) return;

    int moveType = getMoveType(elements);
    if (moveType == CLASSES || moveType == PACKAGES) {
      MoveClassesOrPackagesImpl.doMove(project, elements, targetContainer, callback);
    }
    else if (moveType == FILES || moveType == DIRECTORIES) {
      if (!LOG.assertTrue(targetContainer == null || targetContainer instanceof PsiDirectory)) {
        return;
      }
      MoveFilesOrDirectoriesUtil.doMove(project, elements, (PsiDirectory)targetContainer, callback);
    }
    else if (moveType == MEMBERS) {
      MoveMembersImpl.doMove(project, elements, targetContainer, callback);
    }
    else if (moveType == INNER_TO_UPPER) {
      MoveInnerImpl.doMove(project, elements, callback);
    }
    else if (moveType == INSTANCE_METHOD) {
      new MoveInstanceMethodHandler().invoke(project, elements, null);
    }
    else if (moveType == INNER_TO_UPPER_OR_MEMBERS) {
      SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog((PsiClass)elements[0], project);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      moveType = dialog.getRefactoringType();
      if (moveType == INNER_TO_UPPER) {
        MoveInnerImpl.doMove(project, elements, callback);
      }
      else if (moveType == MEMBERS) {
        MoveMembersImpl.doMove(project, elements, targetContainer, callback);
      }
    }
    else if (moveType == MOVE_OR_REARRANGE_PACKAGE) {
      PsiDirectory[] directories = convertToDirectories(moveType, elements);
      SelectMoveOrRearrangePackageDialog dialog = new SelectMoveOrRearrangePackageDialog(project, directories);
      dialog.show();
      if (!dialog.isOK()) return;
      moveType = dialog.getRefactoringType();
      if (moveType == PACKAGES) {
        MoveClassesOrPackagesImpl.doMove(project, elements, targetContainer, callback);
      } else {
        MoveClassesOrPackagesImpl.doRearrangePackage(project, directories);
      }
    }
  }

  private static PsiDirectory[] convertToDirectories(int moveType, PsiElement[] elements) {
    LOG.assertTrue(moveType == MOVE_OR_REARRANGE_PACKAGE);
    PsiDirectory[] directories = new PsiDirectory[elements.length];
    for (int i = 0; i < directories.length; i++) {
      directories[i] = (PsiDirectory)elements[i];
    }
    return directories;
  }

  /**
   * Must be invoked in AtomicAction
   * target container can be null => means that container is not determined yet and must be spacify by the user
   */
  public static boolean canMove(PsiElement[] elements, PsiElement targetContainer) {
    if (elements == null) {
      throw new IllegalArgumentException("elements cannot be null");
    }
    int moveType = getMoveType(elements);
    if (moveType == NOT_SUPPORTED) {
      return false;
    }
    if (targetContainer == null) {
      return true;
    }
    if (moveType == INNER_TO_UPPER) {
      return targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass)elements[0], false));
    }
    else if (moveType == MEMBERS) {
      return targetContainer instanceof PsiClass && !(targetContainer instanceof PsiAnonymousClass);
    }
    else if (moveType == CLASSES || moveType == PACKAGES) {
      if (targetContainer instanceof PsiPackage) {
        return true;
      }
      if (targetContainer instanceof PsiDirectory) {
        return ((PsiDirectory)targetContainer).getPackage() != null;
      }
      return false;
    }
    else if (moveType == FILES || moveType == DIRECTORIES) {
      return targetContainer instanceof PsiDirectory;
    }
    else {
      return false;
    }
  }

  /**
   * Must be invoked in AtomicAction
   */
  private static int getMoveType(PsiElement[] elements) {
    if (elements == null) {
      throw new IllegalArgumentException("elements cannot be null");
    }
    for (PsiElement element : elements) {
      if (element instanceof JspClass || element instanceof JspHolderMethod) return NOT_SUPPORTED;
    }
    if (MoveFilesOrDirectoriesUtil.canMoveFiles(elements)) {
      return FILES;
    }
    if (MoveFilesOrDirectoriesUtil.canMoveDirectories(elements)) {
      return DIRECTORIES;
    }

    if (MoveFilesOrDirectoriesUtil.canMoveOrRearrangePackages(elements)) {
      return MOVE_OR_REARRANGE_PACKAGE;
    }

    if (elements.length == 1) {
      PsiElement element = elements[0];
      if (element instanceof PsiPackage) {
        return PACKAGES;
      }
      if (element instanceof PsiDirectory) {
        PsiDirectory directory = (PsiDirectory)element;
        return directory.getPackage() != null ? PACKAGES : NOT_SUPPORTED;
      }
      else if (element instanceof PsiField) {
        return MEMBERS;
      }
      else if (element instanceof PsiMethod) {
        return ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC) ? MEMBERS : INSTANCE_METHOD;
      }
      else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (aClass.getParent() instanceof PsiFile) { // top-level class
          return CLASSES;
        }
        else if (aClass.getParent() instanceof PsiClass) { // is inner class
          if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return INNER_TO_UPPER;
          }
          return INNER_TO_UPPER_OR_MEMBERS;
        }
      }
      return NOT_SUPPORTED;
    }
    // the case of multiple members
    // check if this is move packages
    int type = PACKAGES;
    for (PsiElement element : elements) {
      if (element instanceof PsiPackage) {
        continue;
      }
      if (!(element instanceof PsiDirectory)) {
        type = NOT_SUPPORTED;
        break;
      }
      PsiDirectory directory = (PsiDirectory)element;
      if (directory.getPackage() == null) {
        type = NOT_SUPPORTED;
        break;
      }
    }
    if (type != NOT_SUPPORTED) return type;
    // check if this is move classes
    type = CLASSES;
    for (PsiElement element : elements) {
      if (!(element instanceof PsiClass)) {
        type = NOT_SUPPORTED;
        break;
      }
      if (!(element.getParent() instanceof PsiFile)) {
        type = NOT_SUPPORTED;
        break;
      }
    }
    if (type != NOT_SUPPORTED) return type;
    // check if this is move members
    type = MEMBERS;
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        if (!(element.getParent() instanceof PsiClass)) { // is not inner
          type = NOT_SUPPORTED;
          break;
        }
      }
      else if (!(element instanceof PsiField || element instanceof PsiMethod)) {
        type = NOT_SUPPORTED;
        break;
      }
    }
    return type;
  }

  private static class SelectMoveOrRearrangePackageDialog extends DialogWrapper{
    private JRadioButton myRbMovePackage;
    private JRadioButton myRbRearrangePackage;
    private final PsiDirectory[] myDirectories;

    public SelectMoveOrRearrangePackageDialog(Project project, PsiDirectory[] directories) {
      super(project, true);
      myDirectories = directories;
      setTitle(RefactoringBundle.message("select.refactoring.title"));
      init();
    }

    protected JComponent createNorthPanel() {
      return new JLabel(RefactoringBundle.message("what.would.you.like.to.do"));
    }

    public JComponent getPreferredFocusedComponent() {
      return myRbMovePackage;
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.refactoring.move.MoveHandler.SelectRefactoringDialog";
    }


    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());


      final HashSet<String> packages = new HashSet<String>();
      for (PsiDirectory directory : myDirectories) {
        packages.add(directory.getPackage().getQualifiedName());
      }
      final String moveDescription;
      LOG.assertTrue(myDirectories.length > 0);
      LOG.assertTrue(packages.size() > 0);
      if (packages.size() > 1) {
        moveDescription = RefactoringBundle.message("move.packages.to.another.package", packages.size());
      }
      else {
        final String qName = packages.iterator().next();
        moveDescription = RefactoringBundle.message("move.package.to.another.package", qName);
      }

      myRbMovePackage = new JRadioButton();
      myRbMovePackage.setText(moveDescription);
      myRbMovePackage.setSelected(true);

      final String rearrangeDescription;
      if (myDirectories.length > 1) {
        rearrangeDescription = RefactoringBundle.message("move.directories.to.another.source.root", myDirectories.length);
      }
      else {
        rearrangeDescription = RefactoringBundle.message("move.directory.to.another.source.root", myDirectories[0].getVirtualFile().getPresentableUrl());
      }
      myRbRearrangePackage = new JRadioButton();
      myRbRearrangePackage.setText(rearrangeDescription);

      ButtonGroup gr = new ButtonGroup();
      gr.add(myRbMovePackage);
      gr.add(myRbRearrangePackage);

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbMovePackage);
      box.add(myRbRearrangePackage);
      panel.add(box, BorderLayout.CENTER);
      return panel;
    }

    public int getRefactoringType() {
      if (myRbMovePackage.isSelected()) {
        return MoveHandler.PACKAGES;
      }
      if (myRbRearrangePackage.isSelected()) {
        return MoveHandler.MOVE_OR_REARRANGE_PACKAGE;
      }
      return MoveHandler.NOT_SUPPORTED;
    }
  }

  private static class SelectInnerOrMembersRefactoringDialog extends DialogWrapper{
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

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbMoveInner);
      box.add(myRbMoveMembers);
      panel.add(box, BorderLayout.CENTER);
      return panel;
    }

    public int getRefactoringType() {
      if (myRbMoveInner.isSelected()) {
        return MoveHandler.INNER_TO_UPPER;
      }
      if (myRbMoveMembers.isSelected()) {
        return MoveHandler.MEMBERS;
      }
      return MoveHandler.NOT_SUPPORTED;
    }
  }

}
