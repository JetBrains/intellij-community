package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PropertyFileIndex;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;

/**
 * @author max
 */
public class PropertiesFilesManager implements ProjectComponent {
  private final Project myProject;
  private final PropertyChangeListener myListener = new PropertyChangeListener() {
    public void propertyChange(final PropertyChangeEvent evt) {
      if (EncodingManager.PROP_NATIVE2ASCII_SWITCH.equals(evt.getPropertyName()) ||
          EncodingManager.PROP_PROPERTIES_FILES_ENCODING.equals(evt.getPropertyName())
        ) {
        DumbService.getInstance().smartInvokeLater(new Runnable(){
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable(){
              public void run() {
                if (myProject.isDisposed()) return;
                Collection<VirtualFile> filesToRefresh = getAllPropertiesFiles();
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
  };

  public static PropertiesFilesManager getInstance(Project project) {
    return project.getComponent(PropertiesFilesManager.class);
  }

  public PropertiesFilesManager(Project project) {
    myProject = project;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "PropertiesFileManager";
  }

  public void initComponent() {
    EncodingManager.getInstance().addPropertyChangeListener(myListener);
  }

  public void disposeComponent() {
    EncodingManager.getInstance().removePropertyChangeListener(myListener);
  }

  public Collection<VirtualFile> getAllPropertiesFiles() {
    return FileBasedIndex.getInstance().getContainingFiles(PropertyFileIndex.NAME, PropertiesFileType.FILE_TYPE.getName(), GlobalSearchScope.allScope(myProject));
  }

}
