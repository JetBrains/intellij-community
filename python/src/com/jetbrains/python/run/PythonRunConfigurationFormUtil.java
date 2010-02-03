package com.jetbrains.python.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Leonid Shalupov
 */
public class PythonRunConfigurationFormUtil {
  private PythonRunConfigurationFormUtil() {
  }

  public static FileChooserDescriptor addFolderChooser(@NotNull final String title,
                                                       @NotNull final TextFieldWithBrowseButton textField,
                                                       final Project project) {
    final FileChooserDescriptor folderChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    folderChooserDescriptor.setTitle(title);
    textField.addBrowseFolderListener(title, null, project, folderChooserDescriptor);
    return folderChooserDescriptor;
  }

  public static FileChooserDescriptor addFileChooser(@NotNull final String title,
                                                     @NotNull final TextFieldWithBrowseButton textField,
                                                     final Project project) {
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    fileChooserDescriptor.setTitle(title);
    textField.addBrowseFolderListener(title, null, project, fileChooserDescriptor);
    return fileChooserDescriptor;
  }
}
