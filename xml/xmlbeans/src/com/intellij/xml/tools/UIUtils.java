// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class UIUtils {
  private UIUtils() {}

  public static void configureBrowseButton(final Project myProject,
                                       final TextFieldWithBrowseButton wsdlUrl,
                                       final String[] _extensions,
                                       final @DialogTitle String selectFileDialogTitle,
                                       final boolean multipleFileSelection) {
    wsdlUrl.getButton().setToolTipText(XmlBundle.message("browse.button.tooltip"));
    wsdlUrl.getButton().addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, multipleFileSelection) {
            private final List<String> extensions = Arrays.asList(_extensions);

            @Override
            public boolean isFileSelectable(@Nullable VirtualFile virtualFile) {
              if (virtualFile == null) return false;
              return extensions.contains(virtualFile.getExtension());
            }

            @Override
            public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
              return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || isFileSelectable(file));
            }
          };

          fileChooserDescriptor.setTitle(selectFileDialogTitle);

          VirtualFile initialFile = ProjectUtil.guessProjectDir(myProject);
          String selectedItem = wsdlUrl.getTextField().getText();
          if (selectedItem != null && selectedItem.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
            VirtualFile fileByPath = VfsUtilCore
              .findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(VfsUtilCore.fixURLforIDEA(selectedItem)), null);
            if (fileByPath != null) initialFile = fileByPath;
          }

          final VirtualFile[] virtualFiles = FileChooser.chooseFiles(fileChooserDescriptor, myProject, initialFile);
          if (virtualFiles.length == 1) {
            String url = VfsUtilCore.fixIDEAUrl(virtualFiles[0].getUrl());
            wsdlUrl.setText(url);
          }
        }
      }
    );
  }
}
