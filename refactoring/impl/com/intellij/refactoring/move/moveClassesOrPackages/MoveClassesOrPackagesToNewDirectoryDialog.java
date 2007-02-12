package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author ven
 */
public class MoveClassesOrPackagesToNewDirectoryDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesToNewDirectoryDialog");

  private PsiDirectory myDirectory;
  private PsiElement[] myElementsToMove;

  public MoveClassesOrPackagesToNewDirectoryDialog(final PsiDirectory directory, PsiElement[] elementsToMove) {
    super(false);
    setTitle(MoveHandler.REFACTORING_NAME);
    myDirectory = directory;
    myElementsToMove = elementsToMove;
    myDestDirectoryField.setText(directory.getVirtualFile().getPath());
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    //myDestDirectoryField.addBrowseFolderListener(RefactoringBundle.message("select.target.directory"), "", myDirectory.getProject(), descriptor);
    myDestDirectoryField.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VirtualFile[] files = FileChooser.chooseFiles(myDirectory.getProject(), descriptor, directory.getVirtualFile());
        if (files.length == 1) {
          myDestDirectoryField.setText(files[0].getPath());
        }
      }
    });
    if (elementsToMove.length == 1) {
      PsiElement firstElement = elementsToMove[0];
      myNameLabel.setText(RefactoringBundle.message("move.single.class.or.package.name.label", UsageViewUtil.getType(firstElement),
                                                    UsageViewUtil.getLongName(firstElement)));
    }
    else if (elementsToMove.length > 1) {
      myNameLabel.setText((elementsToMove[0] instanceof PsiClass)
                          ? RefactoringBundle.message("move.specified.classes")
                          : RefactoringBundle.message("move.specified.packages"));
    }
    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    mySearchInCommentsAndStringsCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_IN_COMMENTS);
    mySearchForTextOccurrencesCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_FOR_TEXT);

    init();
  }

  private TextFieldWithBrowseButton myDestDirectoryField;
  private JCheckBox mySearchForTextOccurrencesCheckBox;
  private JCheckBox mySearchInCommentsAndStringsCheckBox;
  private JPanel myRootPanel;
  private JLabel myNameLabel;

  public boolean isSearchInNonJavaFiles() {
    return mySearchForTextOccurrencesCheckBox.isSelected();
  }

  public boolean isSearchInComments() {
    return mySearchInCommentsAndStringsCheckBox.isSelected();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  protected void doOKAction() {
    final String path = myDestDirectoryField.getText();
    PsiDirectory directory;
    final Project project = myDirectory.getProject();
    directory = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
      public PsiDirectory compute() {
        try {
          return DirectoryUtil.mkdirs(PsiManager.getInstance(project), path);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return null;
        }
      }
    });
    if (directory == null) {
      Messages.showErrorDialog(project, RefactoringBundle.message("cannot.find.or.create.destination.directory"),
                               RefactoringBundle.message("cannot.move"));
      return;
    }


    super.doOKAction();
    final PsiPackage aPackage = directory.getPackage();
    if (aPackage == null) {
      Messages.showErrorDialog(project, RefactoringBundle.message("destination.directory.does.not.correspond.to.any.package"),
                               RefactoringBundle.message("cannot.move"));
      return;
    }

    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;

    final VirtualFile sourceRoot =
        ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(directory.getVirtualFile());
    final RefactoringFactory factory = RefactoringFactory.getInstance(project);
    final MoveDestination destination = factory.createSourceRootMoveDestination(aPackage.getQualifiedName(), sourceRoot);
    final MoveClassesOrPackagesRefactoring refactoring = factory.createMoveClassesOrPackages(myElementsToMove, destination);
    refactoring.setSearchInComments(searchInComments);
    refactoring.setSearchInNonJavaFiles(searchForTextOccurences);
    refactoring.run();
  }
}



