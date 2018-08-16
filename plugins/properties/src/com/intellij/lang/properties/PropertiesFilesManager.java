// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;

public class PropertiesFilesManager implements ProjectComponent {
  private final Project myProject;

  public static PropertiesFilesManager getInstance(Project project) {
    return project.getComponent(PropertiesFilesManager.class);
  }

  public PropertiesFilesManager(Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    final PropertyChangeListener myListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(final PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
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
    };
    EncodingManager.getInstance().addPropertyChangeListener(myListener,myProject);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "PropertiesFileManager";
  }
}
