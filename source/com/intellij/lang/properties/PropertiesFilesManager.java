package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:00:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFilesManager implements ApplicationComponent {
  private final Set<VirtualFile> myPropertiesFiles = new THashSet<VirtualFile>();
  private VirtualFileListener myVirtualFileListener;
  private final VirtualFileManager myVirtualFileManager;
  private final FileTypeManager myFileTypeManager;

  public static PropertiesFilesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(PropertiesFilesManager.class);
  }

  public PropertiesFilesManager(VirtualFileManager virtualFileManager,FileTypeManager fileTypeManager) {
    myVirtualFileManager = virtualFileManager;
    myFileTypeManager = fileTypeManager;
  }

  private void removeOldFile(final VirtualFileEvent event) {
    myPropertiesFiles.remove(event.getFile());
  }

  private void addNewFile(final VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    addNewFile(file);
  }

  void addNewFile(final VirtualFile file) {
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    if (fileType == PropertiesSupportLoader.FILE_TYPE) {
      myPropertiesFiles.add(file);
    }
  }

  public Collection<VirtualFile> getAllPropertiesFiles() {
    return myPropertiesFiles;
  }

  public void initComponent() {
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
    };
    myVirtualFileManager.addVirtualFileListener(myVirtualFileListener);
  }

  public void disposeComponent() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
  }

  public String getComponentName() {
    return "Properties files manager";
  }

  public void encodingChanged() {
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        for (Editor editor : editors) {
          VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
          if (virtualFile == null) continue;
          FileType fileType = myFileTypeManager.getFileTypeByFile(virtualFile);
          if (fileType == PropertiesSupportLoader.FILE_TYPE) {
            virtualFile.getFileSystem().forceRefreshFile(virtualFile);
          }
        }
      }
    });
  }
}
