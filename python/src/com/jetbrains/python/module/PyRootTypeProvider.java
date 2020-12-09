/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.actions.ContentEntryEditingAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class PyRootTypeProvider {
  public static final ExtensionPointName<PyRootTypeProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyRootTypeProvider");
  protected final VirtualFilePointerListener DUMMY_LISTENER = new VirtualFilePointerListener() {
  };

  public abstract void reset(@NotNull final Disposable disposable, PyContentEntriesEditor editor, @NotNull Module module);

  public abstract void apply(Module module);

  public abstract boolean isModified(Module module);

  public void removeRoot(ContentEntry contentEntry, @NotNull final VirtualFilePointer root, ModifiableRootModel model) {
    getRoots().remove(contentEntry, root);
  }
  public abstract MultiMap<ContentEntry, VirtualFilePointer> getRoots();

  /**
   * Returns the icon for the corresponding root directories in "Project Structure".
   */
  public abstract Icon getIcon();

  /**
   * Returns the name of the action for marking a directory with this root type in "Project Structure".
   * <p>
   * It can be displayed e.g. as the text on a dedicated button in the UI.
   *
   * @see #createRootEntryEditingAction(JTree, Disposable, PyContentEntriesEditor, ModifiableRootModel)
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public abstract String getName();

  /**
   * Returns the description of the action for marking a directory with this root type in "Project Structure".
   * <p>
   * It can be displayed e.g. as the tooltip for a dedicated button in the UI.
   *
   * @see #createRootEntryEditingAction(JTree, Disposable, PyContentEntriesEditor, ModifiableRootModel)
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public abstract String getDescription();

  /**
   * Returns the title of the list of paths to the corresponding directories in "Project Structure".
   * <p>
   * Normally, this title should be in plural form, e.g. "Special Folders".
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getRootsGroupTitle() {
    //noinspection DialogTitleCapitalization
    return getDescription();
  }

  /**
   * @deprecated Use {@link #getRootsGroupColor()}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated
  public Color getColor() {
    throw new AbstractMethodError(getClass().getSimpleName() + " should override getRootsGroupColor()");
  }

  /**
   * Returns the color of the list of paths to the corresponding directories in "Project Structure".
   */
  @NotNull
  public Color getRootsGroupColor() {
    return getColor();
  }

  /**
   * Returns an optional shortcut for the action for marking a directory with this root type in "Project Structure".
   *
   * @see #createRootEntryEditingAction(JTree, Disposable, PyContentEntriesEditor, ModifiableRootModel)
   */
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
      templatePresentation.setText(getName());
      templatePresentation.setDescription(getDescription());
      templatePresentation.setIcon(getIcon());
      myDisposable = disposable;
      myEditor = editor;
      myModel = model;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      final VirtualFile[] selectedFiles = getSelectedFiles();
      return selectedFiles.length != 0 && hasRoot(selectedFiles[0], myEditor);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean isSelected) {
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
}
