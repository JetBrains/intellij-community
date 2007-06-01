/**
 * @author Yura Cangea
 */
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.RootFileElement;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

public class FileTreeBuilder extends AbstractTreeBuilder {
  private final FileChooserDescriptor myChooserDescriptor;

  private VirtualFileAdapter myVirtualFileListener;
  private Runnable myOnInitialized;

  public FileTreeBuilder(JTree tree,
                         DefaultTreeModel treeModel,
                         AbstractTreeStructure treeStructure,
                         Comparator<NodeDescriptor> comparator,
                         FileChooserDescriptor chooserDescriptor,
                         @Nullable Runnable onInitialized) {
    super(tree, treeModel, treeStructure, comparator, false);

    myOnInitialized = onInitialized;

    myChooserDescriptor = chooserDescriptor;
    initRootNode();

    installVirtualFileListener();
  }


  protected void onRootNodeInitialized() {
    if (myOnInitialized != null) {
      myOnInitialized.run();
    }
  }

  private void installVirtualFileListener() {
    myVirtualFileListener = new VirtualFileAdapter() {
      public void propertyChanged(VirtualFilePropertyEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }

      public void fileCreated(VirtualFileEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }

      public void fileDeleted(VirtualFileEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }

      public void fileMoved(VirtualFileMoveEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }
    };

    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
  }

  public final void dispose() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
    super.dispose();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object element = nodeDescriptor.getElement();
    if (element != null) {
      FileElement descriptor = (FileElement)element;
      VirtualFile file = descriptor.getFile();
      if (file != null) {
        if (myChooserDescriptor.isChooseJarContents() && FileElement.isArchive(file)) {
          return true;
        }
        if (file.isDirectory()) {
          return true;
        }
        return false;
      }
    }
    return true;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getElement() instanceof RootFileElement;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
