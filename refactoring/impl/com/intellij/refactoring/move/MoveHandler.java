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
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesToNewDirectoryDialog;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;

public class MoveHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.MoveHandler");

  enum MoveType {
    NOT_SUPPORTED, CLASSES, PACKAGES, MEMBERS, INNER_TO_UPPER, INNER_TO_UPPER_OR_MEMBERS,
    FILES, DIRECTORIES, INSTANCE_METHOD, MOVE_OR_REARRANGE_PACKAGE
  }

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
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
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
      final PsiElement targetContainer = myTargetContainerFinder.getTargetContainer(dataContext);
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
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) { // this is inner class
        FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.move.moveInner");
        if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
          if (containingClass instanceof JspClass) {
            CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, RefactoringBundle.message("move.nonstatic.class.from.jsp.not.supported"), null, project);
            return true;
          }
          MoveInnerImpl.doMove(project, new PsiElement[]{aClass}, null);
        }
        else {
          SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog(aClass, project);
          dialog.show();
          if (dialog.isOK()) {
            MoveType type = dialog.getRefactoringType();
            if (type == MoveType.INNER_TO_UPPER) {
              MoveInnerImpl.doMove(project, new PsiElement[]{aClass}, null);
            }
            else if (type == MoveType.MEMBERS) {
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
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    doMove(project, elements, dataContext != null ? myTargetContainerFinder.getTargetContainer(dataContext) : null, null);
  }

  /**
   * must be invoked in AtomicAction
   */
  public static void doMove(Project project, @NotNull PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    if (elements.length == 0) return;

    MoveType moveType = getMoveType(elements);
    if (moveType == MoveType.CLASSES || moveType == MoveType.PACKAGES) {
      if (tryDirectoryMove ( project, elements, targetContainer, callback)) {
        return;
      }
      if (tryPackageRearrange(project, elements, targetContainer, moveType)) {
        return;
      }
      MoveClassesOrPackagesImpl.doMove(project, elements, targetContainer, callback);
    }
    else if (moveType == MoveType.FILES || moveType == MoveType.DIRECTORIES) {
      if (!LOG.assertTrue(targetContainer == null || targetContainer instanceof PsiDirectory || targetContainer instanceof PsiPackage )) {
        return;
      }
      MoveFilesOrDirectoriesUtil.doMove(project, elements, targetContainer, callback);
    }
    else if (moveType == MoveType.MEMBERS) {
      MoveMembersImpl.doMove(project, elements, targetContainer, callback);
    }
    else if (moveType == MoveType.INNER_TO_UPPER) {
      MoveInnerImpl.doMove(project, elements, callback);
    }
    else if (moveType == MoveType.INSTANCE_METHOD) {
      new MoveInstanceMethodHandler().invoke(project, elements, null);
    }
    else if (moveType == MoveType.INNER_TO_UPPER_OR_MEMBERS) {
      SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog((PsiClass)elements[0], project);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      moveType = dialog.getRefactoringType();
      if (moveType == MoveType.INNER_TO_UPPER) {
        MoveInnerImpl.doMove(project, elements, callback);
      }
      else if (moveType == MoveType.MEMBERS) {
        MoveMembersImpl.doMove(project, elements, targetContainer, callback);
      }
    }
  }

  private static boolean tryDirectoryMove(Project project, final PsiElement[] sourceElements, final PsiElement targetElement, final MoveCallback callback) {
    if (targetElement instanceof PsiDirectory) {
      final PsiElement[] adjustedElements = MoveClassesOrPackagesImpl.adjustForMove(project, sourceElements, targetElement);
      if (adjustedElements != null) {
        if ( CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(adjustedElements),true) ) {
          new MoveClassesOrPackagesToNewDirectoryDialog((PsiDirectory)targetElement, adjustedElements, callback).show();
        }
      }
      return true;
    }
    return false;
  }

  private static boolean tryPackageRearrange(final Project project, final PsiElement[] elements,
                                             final PsiElement targetContainer, MoveType moveType) {
    if (moveType == MoveType.PACKAGES && targetContainer == null && MoveFilesOrDirectoriesUtil.canMoveOrRearrangePackages(elements) ) {
      PsiDirectory[] directories = new PsiDirectory[elements.length];
      System.arraycopy(elements, 0, directories, 0, directories.length);
      SelectMoveOrRearrangePackageDialog dialog = new SelectMoveOrRearrangePackageDialog(project, directories);
      dialog.show();
      if (!dialog.isOK()) return true;
      if (dialog.isPackageRearrageSelected()) {
        MoveClassesOrPackagesImpl.doRearrangePackage(project, directories);
        return true;
      }
    }
    return false;
  }

/**
 * Performs some extra checks (that canMove does not)
 * May replace some elements with others which actulaly shall be moved (e.g. directory->package)
 */
@Nullable
public static PsiElement[] adjustForMove(Project project, final PsiElement[] sourceElements, final PsiElement targetElement) {
  final MoveType type = getMoveType(sourceElements);
  if ( type == MoveType.CLASSES || type == MoveType.PACKAGES ) {
    return MoveClassesOrPackagesImpl.adjustForMove(project,sourceElements, targetElement);
  }
  return sourceElements;
}

  /**
   * Must be invoked in AtomicAction
   * target container can be null => means that container is not determined yet and must be spacify by the user
   */
  public static boolean canMove(@NotNull PsiElement[] elements, PsiElement targetContainer) {
    MoveType moveType = getMoveType(elements);
    if (moveType == MoveType.NOT_SUPPORTED) {
      return false;
    }
    if (targetContainer == null) {
      return true;
    }
    if (moveType == MoveType.INNER_TO_UPPER) {
      return targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass)elements[0], false));
    }
    else if (moveType == MoveType.MEMBERS) {
      return targetContainer instanceof PsiClass && !(targetContainer instanceof PsiAnonymousClass);
    }
    else if (moveType == MoveType.CLASSES || moveType == MoveType.PACKAGES ) {
      if (targetContainer instanceof PsiPackage) {
        return true;
      }
      if (targetContainer instanceof PsiDirectory) {
        return ((PsiDirectory)targetContainer).getPackage() != null;
      }
      if (targetContainer instanceof PsiClass) {
        return moveType == MoveType.CLASSES;
      }
      return false;
    }
    else if (moveType == MoveType.FILES || moveType == MoveType.DIRECTORIES) {
      return targetContainer instanceof PsiDirectory || targetContainer instanceof PsiPackage;
    } else {
      return false;
    }
  }

  /**
   * Must be invoked in AtomicAction
   */
  private static MoveType getMoveType(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (element instanceof JspClass || element instanceof JspHolderMethod) return MoveType.NOT_SUPPORTED;
    }
    if (MoveFilesOrDirectoriesUtil.canMoveFiles(elements)) {
      return MoveType.FILES;
    }
    if (MoveFilesOrDirectoriesUtil.canMoveDirectories(elements)) {
      return MoveType.DIRECTORIES;
    }

    if (elements.length == 1) {
      PsiElement element = elements[0];
      if (element instanceof PsiPackage) {
        return MoveType.PACKAGES;
      }
      if (element instanceof PsiDirectory) {
        PsiDirectory directory = (PsiDirectory)element;
        return directory.getPackage() != null ? MoveType.PACKAGES : MoveType.NOT_SUPPORTED;
      }
      else if (element instanceof PsiField) {
        return MoveType.MEMBERS;
      }
      else if (element instanceof PsiMethod) {
        return ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC) ? MoveType.MEMBERS : MoveType.INSTANCE_METHOD;
      }
      else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (aClass.getParent() instanceof PsiFile) { // top-level class
          return MoveType.CLASSES;
        }
        else if (aClass.getParent() instanceof PsiClass) { // is inner class
          if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return MoveType.INNER_TO_UPPER;
          }
          return MoveType.INNER_TO_UPPER_OR_MEMBERS;
        }
      }
      return MoveType.NOT_SUPPORTED;
    }
    // the case of multiple members
    // check if this is move packages
    MoveType type = MoveType.PACKAGES;
    for (PsiElement element : elements) {
      if (element instanceof PsiPackage) {
        continue;
      }
      if (!(element instanceof PsiDirectory)) {
        type = MoveType.NOT_SUPPORTED;
        break;
      }
      PsiDirectory directory = (PsiDirectory)element;
      if (directory.getPackage() == null) {
        type = MoveType.NOT_SUPPORTED;
        break;
      }
    }
    if (type != MoveType.NOT_SUPPORTED) return type;
    // check if this is move classes
    type = MoveType.CLASSES;
    for (PsiElement element : elements) {
      if (!(element instanceof PsiClass)) {
        type = MoveType.NOT_SUPPORTED;
        break;
      }
      if (!(element.getParent() instanceof PsiFile)) {
        type = MoveType.NOT_SUPPORTED;
        break;
      }
    }
    if (type != MoveType.NOT_SUPPORTED) return type;
    // check if this is move members
    type = MoveType.MEMBERS;
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        if (!(element.getParent() instanceof PsiClass)) { // is not inner
          type = MoveType.NOT_SUPPORTED;
          break;
        }
      }
      else if (!(element instanceof PsiField || element instanceof PsiMethod)) {
        type = MoveType.NOT_SUPPORTED;
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

      new RadioUpDownListener(myRbMovePackage, myRbRearrangePackage);

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbMovePackage);
      box.add(myRbRearrangePackage);
      panel.add(box, BorderLayout.CENTER);
      return panel;
    }

    public boolean isPackageRearrageSelected() {
      return myRbRearrangePackage.isSelected();
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

      new RadioUpDownListener(myRbMoveInner, myRbMoveMembers);

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbMoveInner);
      box.add(myRbMoveMembers);
      panel.add(box, BorderLayout.CENTER);
      return panel;
    }

    public MoveType getRefactoringType() {
      if (myRbMoveInner.isSelected()) {
        return MoveType.INNER_TO_UPPER;
      }
      if (myRbMoveMembers.isSelected()) {
        return MoveType.MEMBERS;
      }
      return MoveType.NOT_SUPPORTED;
    }
  }

  private static class RadioUpDownListener extends KeyAdapter {
    private JRadioButton myRadioButton1;
    private JRadioButton myRadioButton2;

    private RadioUpDownListener(final JRadioButton radioButton1, final JRadioButton radioButton2) {
      myRadioButton1 = radioButton1;
      myRadioButton2 = radioButton2;
      myRadioButton1.addKeyListener(this);
      myRadioButton2.addKeyListener(this);
    }

    public void keyPressed(final KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN) {
        if (myRadioButton1.isSelected()) {
          myRadioButton2.requestFocus();
          myRadioButton2.doClick();
        }
        else {
          myRadioButton1.requestFocus();
          myRadioButton1.doClick();
        }
      }
    }
  }

}
