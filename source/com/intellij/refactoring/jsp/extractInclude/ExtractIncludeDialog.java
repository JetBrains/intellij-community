package com.intellij.refactoring.jsp.extractInclude;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * @author ven
 */
public class ExtractIncludeDialog extends DialogWrapper {
  private TextFieldWithBrowseButton myTargetDirectoryField;
  private JTextField myNameField;
  private final PsiDirectory myCurrentDirectory;

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  private PsiDirectory myTargetDirectory;

  public String getTargetFileName () {
    return myNameField.getText().trim();
  }

  protected ExtractIncludeDialog(final PsiDirectory currentDirectory) {
    super(false);
    myCurrentDirectory = currentDirectory;
    setTitle("Extract Include File");
    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.setBorder(IdeBorderFactory.createBorder());

    JLabel nameLabel = new JLabel("Name for extracted include file:");
    nameLabel.setDisplayedMnemonic('n');
    panel.add(nameLabel);

    myNameField = new JTextField();
    nameLabel.setLabelFor(myNameField);
    myNameField.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        validateOKButton();
      }
    });
    panel.add(myNameField);

    JLabel targetDirLabel = new JLabel("Extract to directory:");
    targetDirLabel.setDisplayedMnemonic('d');
    panel.add(targetDirLabel);

    myTargetDirectoryField = new TextFieldWithBrowseButton();
    myTargetDirectoryField.setText(myCurrentDirectory.getVirtualFile().getPresentableUrl());
    myTargetDirectoryField.addBrowseFolderListener("Select target directory", "The file will be created in this directory",
                                                   null, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    targetDirLabel.setLabelFor(myTargetDirectoryField);
    panel.add(myTargetDirectoryField);

    myTargetDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        validateOKButton();
      }
    });

    validateOKButton();

    return panel;
  }

  private void validateOKButton() {
    final String fileName = myNameField.getText().trim();
    setOKActionEnabled(myTargetDirectoryField.getText().trim().length() > 0 &&
                       fileName.length() > 0 && fileName.indexOf(File.separatorChar) < 0);
  }

  protected void doOKAction() {
    final Project project = myCurrentDirectory.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            String directoryName = myTargetDirectoryField.getText().replace(File.separatorChar, '/');
            try {
              PsiDirectory targetDirectory = DirectoryUtil.mkdirs(PsiManager.getInstance(project), directoryName);
              String targetFileName = getTargetFileName();
              targetDirectory.checkCreateFile(targetFileName);
              final String webPath = ExtractIncludeFileHandler.getWebPath(targetDirectory);
              myTargetDirectory = webPath == null ? null : targetDirectory;
            }
            catch (IncorrectOperationException e) {
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, "Create directory", null);
    if (myTargetDirectory == null){
      RefactoringMessageUtil.showErrorMessage(getTitle(), "Cannot create directory", HelpID.EXTRACT_INCLUDE, project);
      return;
    }
    super.doOKAction();
  }
}
