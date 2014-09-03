/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.roots.impl.ContentFolderBaseImpl;
import com.intellij.openapi.roots.ui.configuration.*;
import com.intellij.openapi.roots.ui.configuration.actions.ContentEntryEditingAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.templateLanguages.TemplatesService;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class PyContentEntriesEditor extends CommonContentEntriesEditor {
  private static final Color TEMPLATES_COLOR = JBColor.MAGENTA;
  private final MultiMap<ContentEntry, VirtualFilePointer> myTemplateRoots = new MultiMap<ContentEntry, VirtualFilePointer>();
  private final Module myModule;
  private Disposable myFilePointersDisposable;

  private final VirtualFilePointerListener DUMMY_LISTENER = new VirtualFilePointerListener() {
    @Override
    public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
    }

    @Override
    public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
    }
  };

  public PyContentEntriesEditor(Module module, ModuleConfigurationState moduleConfigurationState,
                                      JpsModuleSourceRootType<?>... rootTypes) {
    super(module.getName(), moduleConfigurationState, rootTypes);
    myModule = module;
    reset();
  }

  @Override
  protected ContentEntryTreeEditor createContentEntryTreeEditor(Project project) {
    return new MyContentEntryTreeEditor(project, getEditHandlers());
  }

  @Override
  protected List<ContentEntry> addContentEntries(VirtualFile[] files) {
    List<ContentEntry> entries = super.addContentEntries(files);
    addContentEntryPanels(entries.toArray(new ContentEntry[entries.size()]));
    return entries;
  }

  @Override
  public void reset() {
    if (myFilePointersDisposable != null) {
      Disposer.dispose(myFilePointersDisposable);
    }
    myTemplateRoots.clear();

    myFilePointersDisposable = Disposer.newDisposable();
    final TemplatesService instance = TemplatesService.getInstance(myModule);
    if (instance != null) {
      final List<VirtualFile> folders = instance.getTemplateFolders();
      for (VirtualFile folder : folders) {
        ContentEntry contentEntry = findContentEntryForFile(folder);
        if (contentEntry != null) {
          myTemplateRoots.putValue(contentEntry, VirtualFilePointerManager.getInstance().create(folder, myFilePointersDisposable,
                                                                                                DUMMY_LISTENER));
        }
      }
    }

    if (myRootTreeEditor != null) {
      ContentEntryEditor editor = myRootTreeEditor.getContentEntryEditor();
      if(editor!=null) editor.update();
      myRootTreeEditor.update();
    }
  }

  @Nullable
  private ContentEntry findContentEntryForFile(VirtualFile virtualFile) {
    for (ContentEntry contentEntry : getModel().getContentEntries()) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null && VfsUtilCore.isAncestor(file, virtualFile, false)) {
        return contentEntry;
      }
    }
    return null;
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    if (myFilePointersDisposable != null) {
      Disposer.dispose(myFilePointersDisposable);
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    List<VirtualFile> templateRoots = getCurrentState();
    TemplatesService.getInstance(myModule).setTemplateFolders(templateRoots.toArray(new VirtualFile[templateRoots.size()]));
  }

  private List<VirtualFile> getCurrentState() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (ContentEntry entry : myTemplateRoots.keySet()) {
      for (VirtualFilePointer filePointer : myTemplateRoots.get(entry)) {
        result.add(filePointer.getFile());
      }
    }
    return result;
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    final TemplatesService templatesService = TemplatesService.getInstance(myModule);
    if (templatesService != null) {
      List<VirtualFile> original = templatesService.getTemplateFolders();
      List<VirtualFile> current = getCurrentState();

      if (!Comparing.haveEqualElements(original, current)) return true;

    }
    return false;
  }

  @Override
  protected MyContentEntryEditor createContentEntryEditor(String contentEntryUrl) {
    return new MyContentEntryEditor(contentEntryUrl, getEditHandlers());
  }

  protected class MyContentEntryEditor extends ContentEntryEditor {
    private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

    public MyContentEntryEditor(String contentEntryUrl, List<ModuleSourceRootEditHandler<?>> handlers) {
      super(contentEntryUrl, handlers);
    }

    @Override
    protected ModifiableRootModel getModel() {
      return PyContentEntriesEditor.this.getModel();
    }

    public void addListener(ChangeListener changeListener) {
      myEventDispatcher.addListener(changeListener);
    }

    public void removeListener(ChangeListener changeListener) {
      myEventDispatcher.removeListener(changeListener);
    }

    @Override
    protected ContentRootPanel createContentRootPane() {
      return new MyContentRootPanel();
    }

    @Override
    public void deleteContentFolder(ContentEntry contentEntry, ContentFolder folder) {
      if (folder instanceof TemplateRootFolder) {
        final VirtualFile file = folder.getFile();
        if (file != null) {
          removeTemplateRoot(file);
        }
      }
      else {
        super.deleteContentFolder(contentEntry, folder);
      }
    }

    public void addTemplateRoot(@NotNull final VirtualFile file) {
      final VirtualFilePointer root = VirtualFilePointerManager.getInstance().create(file, myFilePointersDisposable, DUMMY_LISTENER);
      myTemplateRoots.putValue(getContentEntry(), root);
      myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
      update();
    }

    public void removeTemplateRoot(@NotNull final VirtualFile file) {
      final VirtualFilePointer root = getTemplateRoot(file);
      if (root != null) {
        myTemplateRoots.remove(getContentEntry(), root);
        myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
        update();
      }
    }

    public boolean hasTemplateRoot(@NotNull final VirtualFile file) {
      return getTemplateRoot(file) != null;
    }

    @Nullable
    public VirtualFilePointer getTemplateRoot(@NotNull final VirtualFile file) {
      for (VirtualFilePointer filePointer : myTemplateRoots.get(getContentEntry())) {
        if (Comparing.equal(filePointer.getFile(), file)) {
          return filePointer;
        }
      }
      return null;
    }

    protected class MyContentRootPanel extends ContentRootPanel {
      public MyContentRootPanel() {
        super(MyContentEntryEditor.this, getEditHandlers());
      }

      @Override
      @NotNull
      protected ContentEntryImpl getContentEntry() {
        //noinspection ConstantConditions
        return (ContentEntryImpl)MyContentEntryEditor.this.getContentEntry();
      }

      @Override
      protected void addFolderGroupComponents() {
        super.addFolderGroupComponents();
        if (!myTemplateRoots.get(getContentEntry()).isEmpty()) {
          final List<TemplateRootFolder> folders = new ArrayList<TemplateRootFolder>(myTemplateRoots.size());
          for (VirtualFilePointer root : myTemplateRoots.get(getContentEntry())) {
            folders.add(new TemplateRootFolder(root, getContentEntry()));
          }
          final JComponent sourcesComponent = createFolderGroupComponent("Template Folders",
                                                                         folders.toArray(new ContentFolder[folders.size()]),
                                                                         TEMPLATES_COLOR, null);
          this.add(sourcesComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTH,
                                                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 10, 0), 0, 0));
        }
      }
    }
  }

  private static class MyContentEntryTreeEditor extends ContentEntryTreeEditor {

    private final ChangeListener myListener = new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        update();
      }
    };

    public MyContentEntryTreeEditor(Project project, List<ModuleSourceRootEditHandler<?>> handlers) {
      super(project, handlers);
    }

    @Override
    public void setContentEntryEditor(ContentEntryEditor newEditor) {
      PyContentEntriesEditor.MyContentEntryEditor existingEditor = getContentEntryEditor();
      if (Comparing.equal(existingEditor, newEditor)) {
        return;
      }
      if (existingEditor != null) {
        existingEditor.removeListener(myListener);
      }
      if (newEditor != null) {
        ((PyContentEntriesEditor.MyContentEntryEditor)newEditor).addListener(myListener);
      }
      super.setContentEntryEditor(newEditor);
    }

    @Override
    public PyContentEntriesEditor.MyContentEntryEditor getContentEntryEditor() {
      return (PyContentEntriesEditor.MyContentEntryEditor)super.getContentEntryEditor();
    }

    @Override
    protected void createEditingActions() {
      super.createEditingActions();

      ContentEntryEditingAction a = new ContentEntryEditingAction(myTree) {
        {
          final Presentation templatePresentation = getTemplatePresentation();
          templatePresentation.setText("Templates");
          templatePresentation.setDescription("Template Folders");
          templatePresentation.setIcon(PythonIcons.Python.TemplateRoot);
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
          final VirtualFile[] selectedFiles = getSelectedFiles();
          return selectedFiles.length != 0 && getContentEntryEditor().hasTemplateRoot(selectedFiles[0]);
        }

        @Override
        public void setSelected(AnActionEvent e, boolean isSelected) {
          final VirtualFile[] selectedFiles = getSelectedFiles();
          assert selectedFiles.length != 0;

          for (VirtualFile selectedFile : selectedFiles) {
            boolean wasSelected = getContentEntryEditor().hasTemplateRoot(selectedFile);
            if (isSelected) {
              if (!wasSelected) {
                getContentEntryEditor().addTemplateRoot(selectedFile);
              }
            }
            else {
              if (wasSelected) {
                getContentEntryEditor().removeTemplateRoot(selectedFile);
              }
            }
          }
        }
      };
      myEditingActionsGroup.add(a);
      a.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_MASK)), myTree);
    }

    @Override
    protected TreeCellRenderer getContentEntryCellRenderer() {
      return new ContentEntryTreeCellRenderer(this, getEditHandlers()) {
        @Override
        protected Icon updateIcon(final ContentEntry entry, final VirtualFile file, final Icon originalIcon) {
          if (getContentEntryEditor().hasTemplateRoot(file)) {
            return PythonIcons.Python.TemplateRoot;
          }
          return super.updateIcon(entry, file, originalIcon);
        }
      };
    }
  }
  private static class TemplateRootFolder extends ContentFolderBaseImpl {
    protected TemplateRootFolder(@NotNull VirtualFilePointer filePointer, @NotNull ContentEntryImpl contentEntry) {
      super(filePointer, contentEntry);
    }
  }

}
