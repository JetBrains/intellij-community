package com.intellij.refactoring.lang;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.lang.jsp.extractInclude.ExtractJspIncludeFileHandler;
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
  private final LanguageFileType myFileType;

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  private PsiDirectory myTargetDirectory;

  public String getTargetFileName () {
    return myNameField.getText().trim() + "." + myFileType.getDefaultExtension();
  }

  public ExtractIncludeDialog(final PsiDirectory currentDirectory, final LanguageFileType fileType) {
    super(false);
    myCurrentDirectory = currentDirectory;
    myFileType = fileType;
    setTitle("Extract Include File");
    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.setBorder(IdeBorderFactory.createBorder());

    JLabel nameLabel = new JLabel("Name for extracted include file (without extension):");
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
              final String webPath = ExtractJspIncludeFileHandler.getWebPath(targetDirectory);
              myTargetDirectory = webPath == null ? null : targetDirectory;
            }
            catch (IncorrectOperationException e) {
              RefactoringMessageUtil.showErrorMessage("Extract Include File", e.getMessage(), null, project);
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, "Create directory", null);
    if (myTargetDirectory == null) return;
    super.doOKAction();
  }
}
