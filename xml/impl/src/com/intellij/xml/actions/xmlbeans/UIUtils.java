package com.intellij.xml.actions.xmlbeans;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.xml.XmlBundle;
import com.intellij.javaee.ExternalResourceManager;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
public class UIUtils {
  private UIUtils() {}

  public static void configureBrowseButton(final Project myProject,
                                       final TextFieldWithBrowseButton wsdlUrl,
                                       final String[] _extensions,
                                       final String selectFileDialogTitle,
                                       final boolean multipleFileSelection) {
    wsdlUrl.getButton().setToolTipText(XmlBundle.message("browse.button.tooltip"));
    wsdlUrl.getButton().addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent actionEvent) {
          final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, multipleFileSelection) {
            private final List<String> extensions = Arrays.asList(_extensions);

            public boolean isFileSelectable(VirtualFile virtualFile) {
              return extensions.contains(virtualFile.getExtension());
            }

            @Override
            public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
              return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || isFileSelectable(file));
            }
          };

          fileChooserDescriptor.setTitle(selectFileDialogTitle);

          final FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(
            fileChooserDescriptor,
            myProject
          );

          VirtualFile initialFile = myProject.getBaseDir();
          String selectedItem = wsdlUrl.getTextField().getText();
          if (selectedItem != null && selectedItem.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
            VirtualFile fileByPath = VfsUtil.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(VfsUtil.fixURLforIDEA(selectedItem)), null);
            if (fileByPath != null) initialFile = fileByPath;
          }

          final VirtualFile[] virtualFiles = fileChooser.choose(initialFile, myProject);
          if (virtualFiles.length == 1) {
            String url = fixIDEAUrl(virtualFiles[0].getUrl());
            wsdlUrl.setText(url);
          }
        }
      }
    );
  }

  public static String fixIDEAUrl(String url) {
    return SystemInfo.isWindows ? VfsUtil.fixIDEAUrl(url) : url;
  }
}
