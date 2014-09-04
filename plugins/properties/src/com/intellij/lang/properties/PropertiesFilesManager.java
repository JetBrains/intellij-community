/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;

/**
 * @author max
 */
public class PropertiesFilesManager extends AbstractProjectComponent {
  public static PropertiesFilesManager getInstance(Project project) {
    return project.getComponent(PropertiesFilesManager.class);
  }

  public PropertiesFilesManager(Project project) {
    super(project);
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
          DumbService.getInstance(myProject).smartInvokeLater(new Runnable(){
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable(){
                @Override
                public void run() {
                  Collection<VirtualFile> filesToRefresh = FileBasedIndex.getInstance()
                    .getContainingFiles(FileTypeIndex.NAME, PropertiesFileType.INSTANCE, GlobalSearchScope.allScope(myProject));
                  VirtualFile[] virtualFiles = VfsUtilCore.toVirtualFileArray(filesToRefresh);
                  FileDocumentManager.getInstance().saveAllDocuments();

                  //force to re-detect encoding
                  for (VirtualFile virtualFile : virtualFiles) {
                    virtualFile.setCharset(null);
                  }
                  FileDocumentManager.getInstance().reloadFiles(virtualFiles);
                }
              });
            }
          });
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
