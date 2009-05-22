package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.search.PropertyFileIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;

/**
 * @author max
 */
public class PropertiesFilesManager {

  public static PropertiesFilesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(PropertiesFilesManager.class);
  }

  public PropertiesFilesManager() {
    EncodingManager.getInstance().addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(final PropertyChangeEvent evt) {
        if (EncodingManager.PROP_NATIVE2ASCII_SWITCH.equals(evt.getPropertyName()) ||
            EncodingManager.PROP_PROPERTIES_FILES_ENCODING.equals(evt.getPropertyName())
          ) {
          DumbService.getInstance().smartInvokeLater(new Runnable(){
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable(){
                public void run() {
                  if (ApplicationManager.getApplication().isDisposed()) return;
                  Collection<VirtualFile> filesToRefresh = new THashSet<VirtualFile>(getAllPropertiesFiles());
                  VirtualFile[] virtualFiles = filesToRefresh.toArray(new VirtualFile[filesToRefresh.size()]);
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
    });
  }

  @Deprecated
  public static Collection<VirtualFile> getAllPropertiesFiles() {
    return FileBasedIndex.getInstance().getContainingFiles(PropertyFileIndex.NAME, PropertiesFileType.FILE_TYPE.getName(), VirtualFileFilter.ALL);
  }

  public static Collection<VirtualFile> getAllPropertiesFiles(@NotNull Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(PropertyFileIndex.NAME, PropertiesFileType.FILE_TYPE.getName(), GlobalSearchScope.allScope(project));
  }

}
