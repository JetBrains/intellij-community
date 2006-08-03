package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MoveClassesOrPackagesDialog extends RefactoringDialog {
  @NonNls private static final String RECENTS_KEY = "MoveClassesOrPackagesDialog.RECENTS_KEY";
  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;

  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog");


  private final JLabel myNameLabel;
  private final JLabel myPromptTo;
  private final ReferenceEditorComboWithBrowseButton myWithBrowseButtonReference;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;
  private JCheckBox myCbMoveToAnotherSourceFolder;
  private String myHelpID;
  private Project myProject;
  private boolean mySearchTextOccurencesEnabled;
  private PsiDirectory myInitialTargetDirectory;
  private final PsiManager myManager;

  public MoveClassesOrPackagesDialog(Project project,
                                     boolean searchTextOccurences,
                                     PsiElement[] elementsToMove,
                                     MoveCallback moveCallback) {
    super(project, true);
    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    setTitle(MoveHandler.REFACTORING_NAME);
    myProject = project;
    mySearchTextOccurencesEnabled = searchTextOccurences;

    myNameLabel = new JLabel();
    myPromptTo = new JLabel(RefactoringBundle.message("move.classes.to.package.label"));
    myManager = PsiManager.getInstance(myProject);
    myWithBrowseButtonReference = new ReferenceEditorComboWithBrowseButton(null, "", myManager, false, RECENTS_KEY);

    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myWithBrowseButtonReference.getChildComponent();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.anchor = GridBagConstraints.CENTER;
    myWithBrowseButtonReference.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          PackageChooserDialog chooser = new PackageChooserDialog(RefactoringBundle.message("choose.destination.package"), myProject);
          chooser.selectPackage(myWithBrowseButtonReference.getText());
          chooser.show();
          PsiPackage aPackage = chooser.getSelectedPackage();
          if (aPackage != null) {
            myWithBrowseButtonReference.setText(aPackage.getQualifiedName());
            validateOKButton();
          }
        }
      }
    );
    JPanel _panel = new JPanel(new BorderLayout(4, 0));
    _panel.add(myPromptTo, BorderLayout.WEST);
    _panel.add(myWithBrowseButtonReference, BorderLayout.CENTER);
    panel.add(_panel, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInComments = new NonFocusableCheckBox();
    myCbSearchInComments.setText(RefactoringBundle.getSearchInCommentsAndStringsText());
    panel.add(myCbSearchInComments, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchTextOccurences = new NonFocusableCheckBox();
    myCbSearchTextOccurences.setText(RefactoringBundle.getSearchForTextOccurrencesText());
    panel.add(myCbSearchTextOccurences, gbConstraints);


    if (!mySearchTextOccurencesEnabled) {
      myCbSearchTextOccurences.setEnabled(false);
      myCbSearchTextOccurences.setVisible(false);
      myCbSearchTextOccurences.setSelected(false);
    }

    gbConstraints.gridx = 0;
    //gbConstraints.gridy = 1;
    gbConstraints.gridwidth = 2;
    myCbMoveToAnotherSourceFolder = new NonFocusableCheckBox();
    myCbMoveToAnotherSourceFolder.setText(RefactoringBundle.message("move.classes.move.to.another.source.folder"));
    panel.add(myCbMoveToAnotherSourceFolder, gbConstraints);

    myWithBrowseButtonReference.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        validateOKButton();
      }
    });

    return panel;
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
    }

    String nameFromCallback = (myMoveCallback instanceof MoveClassesOrPackagesCallback)
                              ? ((MoveClassesOrPackagesCallback) myMoveCallback).getElementsToMoveName()
                              : null;
    if (nameFromCallback != null) {
      myNameLabel.setText(nameFromCallback);
    }
    else if (psiElements.length == 1) {
      PsiElement firstElement = psiElements[0];
      PsiElement parent = firstElement.getParent();
      LOG.assertTrue(parent != null);
      myNameLabel.setText(RefactoringBundle.message("move.single.class.or.package.name.label",
                                                    UsageViewUtil.getType(firstElement), UsageViewUtil.getLongName(firstElement)));
    }
    else if (psiElements.length > 1) {
      myNameLabel.setText((psiElements[0] instanceof PsiClass) ?
                          RefactoringBundle.message("move.specified.classes") :
                          RefactoringBundle.message("move.specified.packages"));
    }

    myCbSearchInComments.setSelected(searchInComments);
    myCbSearchTextOccurences.setSelected(searchForTextOccurences);

    if (getSourceRoots().length == 1) {
      myCbMoveToAnotherSourceFolder.setSelected(false);
      myCbMoveToAnotherSourceFolder.setEnabled(false);
    }
    else {
      myCbMoveToAnotherSourceFolder.setSelected(isTargetDirectoryFixed);
      //myCbMoveToAnotherSourceFolder.setEnabled(myTargetDirectoryFixed);
    }

    validateOKButton();
    myHelpID = helpID;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  private void validateOKButton() {
    String name = myWithBrowseButtonReference.getText().trim();
    if (name.length() == 0) {
      setOKActionEnabled(true);
    }
    else {
      PsiManager manager = myManager;
      setOKActionEnabled(manager.getNameHelper().isQualifiedName(name));
    }
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
    final MoveDestination destination = selectDestination();
    if (destination == null) return;

    RefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages();

    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;
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
          final PsiClass aClass = ((PsiClass)element);
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

      invokeRefactoring(new MoveClassesOrPackagesProcessor(
        getProject(),
        myElementsToMove,
        destination, searchInComments,
        searchForTextOccurences,
        myMoveCallback));
    }
    catch (IncorrectOperationException e) {
      String helpId = HelpID.getMoveHelpID(myElementsToMove[0]);
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), helpId, getProject());
    }
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchTextOccurences.isSelected();
  }

  private MoveDestination selectDestination() {
    final String packageName = myWithBrowseButtonReference.getText().trim();
    if (packageName.length() > 0 && !myManager.getNameHelper().isQualifiedName(packageName)) {
      Messages.showErrorDialog(myProject,
                               RefactoringBundle.message("please.enter.a.valid.target.package.name"),
                               RefactoringBundle.message("move.tltle"));
      return null;
    }
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    PackageWrapper targetPackage = new PackageWrapper(myManager, packageName);
    if (!targetPackage.exists()) {
      final int ret = Messages.showYesNoDialog(myProject,
                                               RefactoringBundle.message("package.does.not.exist", packageName),
                                               RefactoringBundle.message("move.tltle"),
                                               Messages.getQuestionIcon());
      if (ret != 0) return null;
    }

    if (!myCbMoveToAnotherSourceFolder.isSelected()) {
      return new MultipleRootsMoveDestination(targetPackage);
    }

    final VirtualFile[] contentSourceRoots = getSourceRoots();
    if (contentSourceRoots.length == 1) {
      return new AutocreatingSingleSourceRootMoveDestination(targetPackage, contentSourceRoots[0]);
    }
    final VirtualFile sourceRootForFile = MoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, contentSourceRoots, myInitialTargetDirectory);
    if (sourceRootForFile == null) return null;
    return new AutocreatingSingleSourceRootMoveDestination(targetPackage, sourceRootForFile);
  }

  private VirtualFile[] getSourceRoots() {
    return ProjectRootManager.getInstance(myProject).getContentSourceRoots();
  }

}
