/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.actions.ContentEntryEditingAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class PyRootTypeProvider {
  public static final ExtensionPointName<PyRootTypeProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyRootTypeProvider");
  protected final VirtualFilePointerListener DUMMY_LISTENER = new VirtualFilePointerListener() {
    @Override
    public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
    }

    @Override
    public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
    }
  };

  public abstract void reset(@NotNull final Disposable disposable, PyContentEntriesEditor editor, @NotNull Module module);

  public abstract void apply(Module module);

  public abstract boolean isModified(Module module);

  public abstract boolean isMine(ContentFolder folder);

  public void removeRoot(ContentEntry contentEntry, @NotNull final VirtualFilePointer root, ModifiableRootModel model) {
    getRoots().remove(contentEntry, root);
  }
  public abstract MultiMap<ContentEntry, VirtualFilePointer> getRoots();

  public abstract Icon getIcon();

  public abstract String getName();

  public String getNamePlural() {
    return getName() + "s";
  }

  public abstract Color getColor();

  @Nullable
  public CustomShortcutSet getShortcut() {
    return null;
  }

  public void disposeUIResources(@NotNull Module module) {
  }


  protected class RootEntryEditingAction extends ContentEntryEditingAction {
    private final Disposable myDisposable;
    private final PyContentEntriesEditor myEditor;
    private final ModifiableRootModel myModel;

    public RootEntryEditingAction(JTree tree, Disposable disposable, PyContentEntriesEditor editor, ModifiableRootModel model) {
      super(tree);
      final Presentation templatePresentation = getTemplatePresentation();
      templatePresentation.setText(getNamePlural());
      templatePresentation.setDescription(getName() + " Folders");
      templatePresentation.setIcon(getIcon());
      myDisposable = disposable;
      myEditor = editor;
      myModel = model;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      final VirtualFile[] selectedFiles = getSelectedFiles();
      return selectedFiles.length != 0 && hasRoot(selectedFiles[0], myEditor);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean isSelected) {
      final VirtualFile[] selectedFiles = getSelectedFiles();
      assert selectedFiles.length != 0;

      for (VirtualFile selectedFile : selectedFiles) {
        boolean wasSelected = hasRoot(selectedFile, myEditor);
        if (isSelected) {
          if (!wasSelected) {
            final VirtualFilePointer root = VirtualFilePointerManager.getInstance().create(selectedFile, myDisposable, DUMMY_LISTENER);
            addRoot(root, myEditor);
          }
        }
        else {
          if (wasSelected) {
            removeRoot(selectedFile, myEditor, myModel);
          }
        }
      }
    }
  }

  private void addRoot(VirtualFilePointer root, PyContentEntriesEditor editor) {
    editor.getContentEntryEditor().addRoot(this, root);
    editor.getWarningPanel().getValidatorsManager().validate();
  }

  protected void removeRoot(VirtualFile selectedFile, PyContentEntriesEditor editor, ModifiableRootModel model) {
    editor.getContentEntryEditor().removeRoot(null, selectedFile.getUrl(), this);
    editor.getWarningPanel().getValidatorsManager().validate();
  }

  protected boolean hasRoot(VirtualFile file, PyContentEntriesEditor editor) {
    PyContentEntriesEditor.MyContentEntryEditor entryEditor = editor.getContentEntryEditor();
    return entryEditor.getRoot(this, file.getUrl()) != null;
  }

  public abstract ContentEntryEditingAction createRootEntryEditingAction(JTree tree,
                                                                         Disposable disposable, PyContentEntriesEditor editor, ModifiableRootModel model);

  public abstract ContentFolder[] createFolders(ContentEntry contentEntry);
}
