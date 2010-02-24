/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PropertyFileIndex;
import com.intellij.util.Processor;
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

  public void projectOpened() {
    final PropertyChangeListener myListener = new PropertyChangeListener() {
      public void propertyChange(final PropertyChangeEvent evt) {
        if (EncodingManager.PROP_NATIVE2ASCII_SWITCH.equals(evt.getPropertyName()) ||
            EncodingManager.PROP_PROPERTIES_FILES_ENCODING.equals(evt.getPropertyName())
          ) {
          DumbService.getInstance(myProject).smartInvokeLater(new Runnable(){
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable(){
                public void run() {
                  if (myProject.isDisposed()) return;
                  Collection<VirtualFile> filesToRefresh = getAllPropertiesFiles();
                  VirtualFile[] virtualFiles = VfsUtil.toVirtualFileArray(filesToRefresh);
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
    EncodingManager.getInstance().addPropertyChangeListener(myListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        EncodingManager.getInstance().removePropertyChangeListener(myListener);
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return "PropertiesFileManager";
  }

  public Collection<VirtualFile> getAllPropertiesFiles() {
    return FileBasedIndex.getInstance().getContainingFiles(PropertyFileIndex.NAME, PropertiesFileType.FILE_TYPE.getName(), GlobalSearchScope.allScope(myProject));
  }

  public boolean processAllPropertiesFiles(final Processor<VirtualFile> processor) {
    return FileBasedIndex.getInstance().processValues(PropertyFileIndex.NAME, PropertiesFileType.FILE_TYPE.getName(), null, new FileBasedIndex.ValueProcessor<Void>() {
      public boolean process(VirtualFile file, Void value) {
        return processor.process(file);
      }
    }, GlobalSearchScope.allScope(myProject));
  }

}
