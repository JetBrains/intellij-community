package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ClassNameReferenceEditor;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MoveClassesOrPackagesDialog extends RefactoringDialog {
  @NonNls private static final String RECENTS_KEY = "MoveClassesOrPackagesDialog.RECENTS_KEY";
  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;

  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog");


  private JLabel myNameLabel;
  private ReferenceEditorComboWithBrowseButton myWithBrowseButtonReference;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;
  private JCheckBox myCbMoveToAnotherSourceFolder;
  private String myHelpID;
  private final boolean mySearchTextOccurencesEnabled;
  private PsiDirectory myInitialTargetDirectory;
  private final PsiManager myManager;
  private JPanel myMainPanel;
  private JRadioButton myToPackageRadioButton;
  private JRadioButton myMakeInnerClassOfRadioButton;
  private ReferenceEditorComboWithBrowseButton myClassPackageChooser;
  private JPanel myCardPanel;
  private ReferenceEditorWithBrowseButton myInnerClassChooser;
  private boolean myHavePackages;

  public MoveClassesOrPackagesDialog(Project project,
                                     boolean searchTextOccurences,
                                     PsiElement[] elementsToMove,
                                     final PsiElement initialTargetElement,
                                     MoveCallback moveCallback) {
    super(project, true);
    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    myManager = PsiManager.getInstance(myProject);
    setTitle(MoveHandler.REFACTORING_NAME);
    mySearchTextOccurencesEnabled = searchTextOccurences;

    selectInitialCard();

    init();

    if (initialTargetElement instanceof PsiClass) {
      myMakeInnerClassOfRadioButton.setSelected(true);

      myInnerClassChooser.setText(((PsiClass)initialTargetElement).getQualifiedName());

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myInnerClassChooser.requestFocus();
        }
      }, ModalityState.stateForComponent(myMainPanel));
    }
    else if (initialTargetElement instanceof PsiPackage) {
      myClassPackageChooser.setText(((PsiPackage)initialTargetElement).getQualifiedName());
    }

    updateControlsEnabled();
    myToPackageRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControlsEnabled();
        myClassPackageChooser.requestFocus();
      }
    });
    myMakeInnerClassOfRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControlsEnabled();
        myInnerClassChooser.requestFocus();
      }
    });
  }

  private void updateControlsEnabled() {
    myClassPackageChooser.setEnabled(myToPackageRadioButton.isSelected());
    myInnerClassChooser.setEnabled(myMakeInnerClassOfRadioButton.isSelected());
    myCbMoveToAnotherSourceFolder.setEnabled(isMoveToPackage());
    validateButtons();
  }

  private void selectInitialCard() {
    myHavePackages = false;
    for (PsiElement psiElement : myElementsToMove) {
      if (!(psiElement instanceof PsiClass)) {
        myHavePackages = true;
        break;
      }
    }
    CardLayout cardLayout = (CardLayout)myCardPanel.getLayout();
    cardLayout.show(myCardPanel, myHavePackages ? "Package" : "Class");
  }

  public JComponent getPreferredFocusedComponent() {
    return myWithBrowseButtonReference.getChildComponent();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private void createUIComponents() {
    myWithBrowseButtonReference = createPackageChooser();
    myClassPackageChooser = createPackageChooser();

    myInnerClassChooser = new ClassNameReferenceEditor(PsiManager.getInstance(myProject), null, myProject.getProjectScope());
    myInnerClassChooser.addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        validateButtons();
      }
    });
  }

  private ReferenceEditorComboWithBrowseButton createPackageChooser() {
    final ReferenceEditorComboWithBrowseButton packageChooser =
      new ReferenceEditorComboWithBrowseButton(null, "", PsiManager.getInstance(myProject), false, RECENTS_KEY);
    packageChooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PackageChooserDialog chooser = new PackageChooserDialog(RefactoringBundle.message("choose.destination.package"), myProject);
        chooser.selectPackage(packageChooser.getText());
        chooser.show();
        PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          packageChooser.setText(aPackage.getQualifiedName());
          validateButtons();
        }
      }
    });
    packageChooser.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        validateButtons();
      }
    });

    return packageChooser;
  }

  protected JComponent createNorthPanel() {
    if (!mySearchTextOccurencesEnabled) {
      myCbSearchTextOccurences.setEnabled(false);
      myCbSearchTextOccurences.setVisible(false);
      myCbSearchTextOccurences.setSelected(false);
    }

    return myMainPanel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog";
  }

  public void setData(PsiElement[] psiElements,
                      String targetPackageName,
                      PsiDirectory initialTargetDirectory,
                      boolean isTargetDirectoryFixed,
                      boolean searchInComments,
                      boolean searchForTextOccurences,
                      String helpID) {
    myInitialTargetDirectory = initialTargetDirectory;
    if (targetPackageName.length() != 0) {
      myWithBrowseButtonReference.prependItem(targetPackageName);
      myClassPackageChooser.prependItem(targetPackageName);
    }

    String nameFromCallback = myMoveCallback instanceof MoveClassesOrPackagesCallback
                              ? ((MoveClassesOrPackagesCallback)myMoveCallback).getElementsToMoveName()
                              : null;
    if (nameFromCallback != null) {
      myNameLabel.setText(nameFromCallback);
    }
    else if (psiElements.length == 1) {
      PsiElement firstElement = psiElements[0];
      PsiElement parent = firstElement.getParent();
      LOG.assertTrue(parent != null);
      myNameLabel.setText(RefactoringBundle.message("move.single.class.or.package.name.label", UsageViewUtil.getType(firstElement),
                                                    UsageViewUtil.getLongName(firstElement)));
    }
    else if (psiElements.length > 1) {
      myNameLabel.setText(psiElements[0] instanceof PsiClass
                          ? RefactoringBundle.message("move.specified.classes")
                          : RefactoringBundle.message("move.specified.packages"));
    }
    selectInitialCard();

    myCbSearchInComments.setSelected(searchInComments);
    myCbSearchTextOccurences.setSelected(searchForTextOccurences);

    if (getSourceRoots().length == 1) {
      myCbMoveToAnotherSourceFolder.setSelected(false);
      myCbMoveToAnotherSourceFolder.setEnabled(false);
    }
    else {
      myCbMoveToAnotherSourceFolder.setSelected(!isTargetDirectoryFixed);
    }

    validateButtons();
    myHelpID = helpID;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  private boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  protected boolean areButtonsValid() {
    if (isMoveToPackage()) {
      String name = getTargetPackage().trim();
      return name.length() == 0 || myManager.getNameHelper().isQualifiedName(name);
    }
    else {
      return findTargetClass() != null && getValidationError() == null;
    }
  }

  protected void validateButtons() {
    super.validateButtons();
    setErrorText(getValidationError());
  }

  @Nullable
  private String getValidationError() {
    if (!isMoveToPackage()) {
      return verifyInnerClassDestination();
    }
    return null;
  }

  @Nullable
  private PsiClass findTargetClass() {
    String name = myInnerClassChooser.getText().trim();
    return myManager.findClass(name, myProject.getProjectScope());
  }

  private boolean isMoveToPackage() {
    return myHavePackages || myToPackageRadioButton.isSelected();
  }

  private String getTargetPackage() {
    return myHavePackages ? myWithBrowseButtonReference.getText() : myClassPackageChooser.getText();
  }

  private static String verifyDestinationForElement(final PsiElement element, final MoveDestination moveDestination) {
    final String message;
    if (element instanceof PsiDirectory) {
      message = moveDestination.verify((PsiDirectory)element);
    }
    else if (element instanceof PsiPackage) {
      message = moveDestination.verify((PsiPackage)element);
    }
    else {
      message = moveDestination.verify(element.getContainingFile());
    }
    return message;
  }

  protected void doAction() {
    if (isMoveToPackage()) {
      invokeMoveToPackage();
    }
    else {
      invokeMoveToInner();
    }
  }

  private void invokeMoveToPackage() {
    final MoveDestination destination = selectDestination();
    if (destination == null) return;

    saveRefactoringSettings();
    PsiManager manager = PsiManager.getInstance(getProject());
    for (final PsiElement element : myElementsToMove) {
      String message = verifyDestinationForElement(element, destination);
      if (message != null) {
        String helpId = HelpID.getMoveHelpID(myElementsToMove[0]);
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), message, helpId, getProject());
        return;
      }
    }
    try {
      for (PsiElement element : myElementsToMove) {
        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          PsiElement toAdd;
          if (aClass.getContainingFile() instanceof PsiJavaFile && ((PsiJavaFile)aClass.getContainingFile()).getClasses().length > 1) {
            toAdd = aClass;
          }
          else {
            toAdd = aClass.getContainingFile();
          }

          final PsiDirectory targetDirectory = destination.getTargetIfExists(element.getContainingFile());
          if (targetDirectory != null) {
            manager.checkMove(toAdd, targetDirectory);
          }
        }
      }

      invokeRefactoring(new MoveClassesOrPackagesProcessor(myProject, myElementsToMove, destination, isSearchInComments(),
                                                           isSearchInNonJavaFiles(), myMoveCallback));
    }
    catch (IncorrectOperationException e) {
      String helpId = HelpID.getMoveHelpID(myElementsToMove[0]);
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), helpId, getProject());
    }
  }

  private void saveRefactoringSettings() {
    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;
    refactoringSettings.MOVE_PREVIEW_USAGES = isPreviewUsages();
  }

  @Nullable
  private String verifyInnerClassDestination() {
    PsiClass targetClass = findTargetClass();
    if (targetClass == null) return null;

    for (PsiElement element : myElementsToMove) {
      if (PsiTreeUtil.isAncestor(element, targetClass, false)) {
        return RefactoringBundle.message("move.class.to.inner.move.to.self.error");
      }
    }

    while (targetClass != null) {
      if (targetClass.getContainingClass() != null && !targetClass.hasModifierProperty(PsiModifier.STATIC)) {
        return RefactoringBundle.message("move.class.to.inner.nonstatic.error");
      }
      targetClass = targetClass.getContainingClass();
    }

    return null;
  }

  private void invokeMoveToInner() {
    saveRefactoringSettings();
    PsiClass targetClass = findTargetClass();
    for (int i = 0; i < myElementsToMove.length; i++) {
      PsiClass psiClass = (PsiClass)myElementsToMove[i];
      // fire callback after last element has been processed
      invokeRefactoring(new MoveClassToInnerProcessor(myProject, psiClass, targetClass, isSearchInComments(), isSearchInNonJavaFiles(),
                                                      i == myElementsToMove.length - 1 ? myMoveCallback : null));
    }
  }

  private boolean isSearchInNonJavaFiles() {
    return myCbSearchTextOccurences.isSelected();
  }

  private MoveDestination selectDestination() {
    final String packageName = getTargetPackage().trim();
    if (packageName.length() > 0 && !myManager.getNameHelper().isQualifiedName(packageName)) {
      Messages.showErrorDialog(myProject, RefactoringBundle.message("please.enter.a.valid.target.package.name"),
                               RefactoringBundle.message("move.tltle"));
      return null;
    }
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    PackageWrapper targetPackage = new PackageWrapper(myManager, packageName);
    if (!targetPackage.exists()) {
      final int ret = Messages.showYesNoDialog(myProject, RefactoringBundle.message("package.does.not.exist", packageName),
                                               RefactoringBundle.message("move.tltle"), Messages.getQuestionIcon());
      if (ret != 0) return null;
    }

    if (!myCbMoveToAnotherSourceFolder.isSelected()) {
      return new MultipleRootsMoveDestination(targetPackage);
    }

    final VirtualFile[] contentSourceRoots = getSourceRoots();
    if (contentSourceRoots.length == 1) {
      return new AutocreatingSingleSourceRootMoveDestination(targetPackage, contentSourceRoots[0]);
    }
    final VirtualFile sourceRootForFile =
      MoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, contentSourceRoots, myInitialTargetDirectory);
    if (sourceRootForFile == null) return null;
    return new AutocreatingSingleSourceRootMoveDestination(targetPackage, sourceRootForFile);
  }

  private VirtualFile[] getSourceRoots() {
    return ProjectRootManager.getInstance(myProject).getContentSourceRoots();
  }
}
