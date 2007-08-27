package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.Disposable;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.Map;

/**
 * author: lesya
 */
public abstract class FileOrDirectoryTreeNode extends AbstractTreeNode implements VirtualFilePointerListener, Disposable {
  private final static Map<FileStatus, SimpleTextAttributes> myFileStatusToAttributeMap = new com.intellij.util.containers.HashMap<FileStatus, SimpleTextAttributes>();
  private final SimpleTextAttributes myInvalidAttributes;
  private final Project myProject;
  protected final File myFile;
  private final String myName;

  protected FileOrDirectoryTreeNode(@NotNull String path, SimpleTextAttributes invalidAttributes,
                                 Project project, String parentPath) {
    String preparedPath = path.replace(File.separatorChar, '/');
    String url = VirtualFileManager.constructUrl(LocalFileSystem.getInstance().getProtocol(),
                                                 preparedPath);
    setUserObject(VirtualFilePointerManager.getInstance().create(url, this));
    myFile = new File(getFilePath());
    myInvalidAttributes = invalidAttributes;
    myProject = project;
    myName = parentPath == null ? myFile.getAbsolutePath() : myFile.getName();
  }

  public String getName() {
    return myName;
  }

  protected String getFilePath() {
    return getFilePointer().getPresentableUrl();
  }

  public void beforeValidityChanged(VirtualFilePointer[] pointers) {
  }

  public void validityChanged(VirtualFilePointer[] pointers) {
    if (!getFilePointer().isValid()) {
      AbstractTreeNode parent = (AbstractTreeNode) getParent();
      if ((parent != null) && parent.getSupportsDeletion()) {
        getTreeModel().removeNodeFromParent(this);
      }
      else {
        if (getTree() != null)
          getTree().repaint();
      }
    }
  }

  public VirtualFilePointer getFilePointer() {
    return ((VirtualFilePointer)getUserObject());
  }

  public SimpleTextAttributes getAttributes() {
    if (!getFilePointer().isValid()) {
      return myInvalidAttributes;
    }
    VirtualFile file = getFilePointer().getFile();
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    FileStatus status = fileStatusManager.getStatus(file);
    return getAttributesFor(status);
  }

  private static SimpleTextAttributes getAttributesFor(FileStatus status) {
    Color color = status.getColor();
    if (color == null) color = Color.black;

    if (!myFileStatusToAttributeMap.containsKey(status)) {
      myFileStatusToAttributeMap.put(status, new SimpleTextAttributes(Font.PLAIN, color));
    }
    return myFileStatusToAttributeMap.get(status);
  }

  public boolean getSupportsDeletion() {
    AbstractTreeNode parent = ((AbstractTreeNode)getParent());
    if (parent == null) return false;
    return parent.getSupportsDeletion();
  }

  public void dispose() {
    VirtualFilePointerManager.getInstance().kill((VirtualFilePointer) getUserObject(), this);
  }
}
