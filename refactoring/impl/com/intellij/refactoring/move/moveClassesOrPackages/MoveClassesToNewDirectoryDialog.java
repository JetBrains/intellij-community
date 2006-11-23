package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author ven
 */
public class MoveClassesToNewDirectoryDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesToNewDirectoryDialog");

  private PsiDirectory myDirectory;
  private PsiElement[] myElementsToMove;

  public MoveClassesToNewDirectoryDialog(final PsiDirectory directory, PsiElement[] elementsToMove) {
    super(false);
    myDirectory = directory;
    myElementsToMove = elementsToMove;
    myDestDirectoryField.setText(directory.getVirtualFile().getPath());
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    //myDestDirectoryField.addBrowseFolderListener(RefactoringBundle.message("select.target.directory"), "", myDirectory.getProject(), descriptor);
    myDestDirectoryField.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VirtualFile[] files = FileChooser.chooseFiles(myDirectory.getProject(), descriptor, directory.getVirtualFile());
        if (files != null && files.length == 1) {
          myDestDirectoryField.setText(files[0].getPath());
        }
      }
    });
    init();
  }

  private TextFieldWithBrowseButton myDestDirectoryField;
  private JCheckBox mySearchForTextOccurrencesCheckBox;
  private JCheckBox mySearchInCommentsAndStringsCheckBox;
  private JPanel myRootPanel;

  public boolean isSearchForTextOccurrences() {
    return mySearchForTextOccurrencesCheckBox.isSelected();
  }

  public boolean isSearchInCommentsAndStrings() {
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
    final VirtualFile sourceRoot =
        ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(directory.getVirtualFile());
    final RefactoringFactory factory = RefactoringFactory.getInstance(project);
    final MoveDestination destination = factory.createSourceRootMoveDestination(aPackage.getQualifiedName(), sourceRoot);
    factory.createMoveClassesOrPackages(myElementsToMove, destination).run();
  }
}



