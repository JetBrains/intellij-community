// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.xml.XmlBundle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

final class UIUtils {
  private UIUtils() {}

  public static void configureBrowseButton(
    Project myProject,
    TextFieldWithBrowseButton wsdlUrl,
    String extension,
    @DialogTitle String selectFileDialogTitle,
    boolean multipleFileSelection
  ) {
    wsdlUrl.getButton().setToolTipText(XmlBundle.message("browse.button.tooltip"));
    wsdlUrl.getButton().addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          var fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, multipleFileSelection)
            .withExtensionFilter(extension)
            .withTitle(selectFileDialogTitle);

          var initialFile = ProjectUtil.guessProjectDir(myProject);
          var selectedItem = wsdlUrl.getTextField().getText();
          if (selectedItem != null && selectedItem.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
            var fileByPath = VfsUtilCore.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(VfsUtilCore.fixURLforIDEA(selectedItem)), null);
            if (fileByPath != null) initialFile = fileByPath;
          }

          var virtualFiles = FileChooser.chooseFiles(fileChooserDescriptor, myProject, initialFile);
          if (virtualFiles.length == 1) {
            String url = VfsUtilCore.fixIDEAUrl(virtualFiles[0].getUrl());
            wsdlUrl.setText(url);
          }
        }
      }
    );
  }
}
