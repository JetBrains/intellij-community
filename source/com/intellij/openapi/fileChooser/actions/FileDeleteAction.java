package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class FileDeleteAction extends DeleteAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.chooser.FileDeleteAction");
  private final DeleteProvider myDeleteProvider;

  private FileDeleteAction(DeleteProvider fileChooser) {
    super("Delete...", "Delete", IconLoader.getIcon("/actions/delete.png"));
    myDeleteProvider = fileChooser;
  }

  public FileDeleteAction(FileSystemTree tree) {
    this(new FileSystemDeleteProvider(tree));
    setEnabledInModalContext(true);
    registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE).getShortcutSet(),
      tree.getTree()
    );
  }

  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    return myDeleteProvider;
  }

  private static final class FileSystemDeleteProvider implements DeleteProvider {
    private final FileSystemTree myTree;

    public FileSystemDeleteProvider(FileSystemTree tree) {
      myTree = tree;
    }

    public boolean canDeleteElement(DataContext dataContext) {return myTree.selectionExists(); }

    public void deleteElement(DataContext dataContext) { deleteSelectedFiles(); }

    void deleteSelectedFiles() {
      final VirtualFile[] files = myTree.getSelectedFiles();
      if (files.length == 0) return;

      String message = createConfirmationMessage(files);
      int returnValue = Messages.showYesNoDialog(message, "Delete", Messages.getQuestionIcon());
      if (returnValue != 0) return;

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for (int i = 0; i < files.length; i++) {
            final VirtualFile file = files[i];
            try {
              file.delete(this);
            }
            catch (IOException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                          public void run() {
                            Messages.showMessageDialog("Could not erase file or folder: " + file.getName(), "Error", Messages.getErrorIcon());
                          }
                        });
            }
          }
        }
      }
      );
    }

    private String createConfirmationMessage(VirtualFile[] filesToDelete) {
      String deleteWhat;
      if (filesToDelete.length == 1){
        deleteWhat = filesToDelete[0].isDirectory() ? "folder" : "file";
      }
      else {
        boolean hasFiles = false;
        boolean hasFolders = false;
        for (int i = 0; i < filesToDelete.length; i++) {
          VirtualFile file = filesToDelete[i];
          boolean isDirectory = file.isDirectory();
          hasFiles |= !isDirectory;
          hasFolders |= isDirectory;
        }
        LOG.assertTrue(hasFiles || hasFolders);
        if (hasFiles && hasFolders) deleteWhat = "files and directories";
        else if (hasFolders) deleteWhat = "folders";
        else deleteWhat = "files";
      }
      return "Are you sure you want to delete selected " + deleteWhat + "?";
    }
  }
}
