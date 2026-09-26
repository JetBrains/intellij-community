// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerListener;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class Native2AsciiListener implements EncodingManagerListener {
  private final Project myProject;

  public Native2AsciiListener(@NotNull Project project) {myProject = project;}

  @Override
  public void propertyChanged(@Nullable Document document, @NotNull String propertyName, Object oldValue, Object newValue) {
    if (EncodingManager.PROP_NATIVE2ASCII_SWITCH.equals(propertyName) ||
        EncodingManager.PROP_PROPERTIES_FILES_ENCODING.equals(propertyName)
    ) {
      DumbService.getInstance(myProject).smartInvokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
        Collection<VirtualFile> filesToRefresh = FileTypeIndex.getFiles(PropertiesFileType.INSTANCE, GlobalSearchScope.allScope(myProject));
        VirtualFile[] virtualFiles = VfsUtilCore.toVirtualFileArray(filesToRefresh);
        FileDocumentManager.getInstance().saveAllDocuments();

        //force to re-detect encoding
        for (VirtualFile virtualFile : virtualFiles) {
          virtualFile.setCharset(null);
        }
        FileDocumentManager.getInstance().reloadFiles(virtualFiles);
      }));
    }
  }
}
