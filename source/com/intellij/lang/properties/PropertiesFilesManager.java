package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.containers.ConcurrentHashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class PropertiesFilesManager implements ApplicationComponent {
  private final Set<VirtualFile> myPropertiesFiles = new ConcurrentHashSet<VirtualFile>();
  private final VirtualFileListener myVirtualFileListener;
  private final VirtualFileManager myVirtualFileManager;
  private final FileTypeManager myFileTypeManager;
  private final List<PropertiesFileListener> myPropertiesFileListeners = new ArrayList<PropertiesFileListener>();

  public static PropertiesFilesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(PropertiesFilesManager.class);
  }

  public PropertiesFilesManager(VirtualFileManager virtualFileManager,FileTypeManager fileTypeManager) {
    myVirtualFileManager = virtualFileManager;
    myFileTypeManager = fileTypeManager;
    myVirtualFileListener = new VirtualFileAdapter() {
      public void fileCreated(VirtualFileEvent event) {
        addNewFile(event);
      }

      public void fileDeleted(VirtualFileEvent event) {
        removeOldFile(event);
      }

      public void fileMoved(VirtualFileMoveEvent event) {
        removeOldFile(event);
        addNewFile(event);
      }

      public void propertyChanged(VirtualFilePropertyEvent event) {
        VirtualFile file = event.getFile();
        fileChanged(file, event);
      }

      public void contentsChanged(VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        fileChanged(file, null);
      }
    };
    EncodingManager.getInstance().addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(final PropertyChangeEvent evt) {
        if (EncodingManager.PROP_NATIVE2ASCII_SWITCH.equals(evt.getPropertyName()) ||
            EncodingManager.PROP_PROPERTIES_FILES_ENCODING.equals(evt.getPropertyName())
          ) {
          encodingChanged();
        }
      }
    });
  }

  private void removeOldFile(final VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    if (fileType == StdFileTypes.PROPERTIES) {
      firePropertiesFileRemoved(file);
    }
    removeFile(file);
  }

  private void removeFile(final VirtualFile file) {
    myPropertiesFiles.remove(file);
  }

  private void addNewFile(final VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    addNewFile(file);
  }

  // returns true if file is of properties file type
  boolean addNewFile(final VirtualFile file) {
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    if (fileType == StdFileTypes.PROPERTIES) {
      if (myPropertiesFiles.add(file)) {
        firePropertiesFileAdded(file);
      }
      return true;
    }
    return false;
  }

  public Collection<VirtualFile> getAllPropertiesFiles() {
    return myPropertiesFiles;
  }

  public void initComponent() {
    myVirtualFileManager.addVirtualFileListener(myVirtualFileListener);
  }

  private void fileChanged(final VirtualFile file, final VirtualFilePropertyEvent event) {
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    if (fileType == StdFileTypes.PROPERTIES) {
      firePropertiesFileChanged(file, event);
    }
    else {
      removeFile(file);
    }
  }

  public void disposeComponent() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
  }

  @NotNull
  public String getComponentName() {
    return "Properties files manager";
  }

  private void encodingChanged() {
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
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

  public void addPropertiesFileListener(PropertiesFileListener fileListener) {
    myPropertiesFileListeners.add(fileListener);
  }
  public void removePropertiesFileListener(PropertiesFileListener fileListener) {
    myPropertiesFileListeners.remove(fileListener);
  }

  private void firePropertiesFileAdded(VirtualFile propertiesFile) {
    for (PropertiesFileListener listener : myPropertiesFileListeners) {
      listener.fileAdded(propertiesFile);
      listener.fileChanged(propertiesFile, null);
    }
  }
  private void firePropertiesFileRemoved(VirtualFile propertiesFile) {
    for (PropertiesFileListener listener : myPropertiesFileListeners) {
      listener.fileRemoved(propertiesFile);
      listener.fileChanged(propertiesFile, null);
    }
  }
  private void firePropertiesFileChanged(VirtualFile propertiesFile, final VirtualFilePropertyEvent event) {
    for (PropertiesFileListener listener : myPropertiesFileListeners) {
      listener.fileChanged(propertiesFile, event);
    }
  }

  public static interface PropertiesFileListener {
    void fileAdded(VirtualFile propertiesFile);
    void fileRemoved(VirtualFile propertiesFile);
    void fileChanged(VirtualFile propertiesFile, final VirtualFilePropertyEvent event);
  }
}
