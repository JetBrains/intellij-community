/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.roots.ui.configuration.*;
import com.intellij.openapi.roots.ui.configuration.actions.ContentEntryEditingAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.List;

public class PyContentEntriesEditor extends CommonContentEntriesEditor {
  private final PyRootTypeProvider[] myRootTypeProviders;
  private final Module myModule;
  private Disposable myFilePointersDisposable;
  private MyContentEntryEditor myContentEntryEditor;
  private FacetErrorPanel myWarningPanel;

  public PyContentEntriesEditor(Module module, ModuleConfigurationState moduleConfigurationState,
                                      JpsModuleSourceRootType<?>... rootTypes) {
    super(module.getName(), moduleConfigurationState, rootTypes);
    myRootTypeProviders = Extensions.getExtensions(PyRootTypeProvider.EP_NAME);
    myModule = module;
    myWarningPanel = new FacetErrorPanel();
    reset();
  }

  public MyContentEntryEditor getContentEntryEditor() {
    return myContentEntryEditor;
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

  public ContentEntry[] getContentEntries() {
    return getModel().getContentEntries();
  }

  @Override
  public void reset() {
    if (myFilePointersDisposable != null) {
      Disposer.dispose(myFilePointersDisposable);
    }

    myFilePointersDisposable = Disposer.newDisposable();
    for (PyRootTypeProvider provider : myRootTypeProviders) {
      provider.reset(myFilePointersDisposable, this, myModule);
    }

    if (myRootTreeEditor != null) {
      ContentEntryEditor editor = myRootTreeEditor.getContentEntryEditor();
      if(editor!=null) editor.update();
      myRootTreeEditor.update();
    }
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    if (myFilePointersDisposable != null) {
      Disposer.dispose(myFilePointersDisposable);
    }

    for (PyRootTypeProvider provider : myRootTypeProviders) {
      provider.disposeUIResources(myModule);
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    for (PyRootTypeProvider provider : myRootTypeProviders) {
      provider.apply(myModule);
    }
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    for (PyRootTypeProvider provider : myRootTypeProviders) {
     if (provider.isModified(myModule)) {
       return true;
     }
    }
    return false;
  }

  @Override
  protected ContentEntryEditor createContentEntryEditor(String contentEntryUrl) {
    myContentEntryEditor = new MyContentEntryEditor(contentEntryUrl, getEditHandlers());
    return myContentEntryEditor;
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
      for (PyRootTypeProvider provider : myRootTypeProviders) {
        if (provider.isMine(folder)) {
          removeRoot(contentEntry, folder.getUrl(), provider);
          return;
        }
      }
      super.deleteContentFolder(contentEntry, folder);
    }

    public void removeRoot(@Nullable ContentEntry contentEntry, String folder, PyRootTypeProvider provider) {
      if (contentEntry == null) {
        contentEntry = getContentEntry();
      }
      VirtualFilePointer root = getRoot(provider, folder);
      if (root != null) {
        provider.removeRoot(contentEntry, root, getModel());
        fireUpdate();
      }
    }

    public void fireUpdate() {
      myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
      update();
    }

    public VirtualFilePointer getRoot(PyRootTypeProvider provider, @NotNull final String url) {
      for (VirtualFilePointer filePointer : provider.getRoots().get(getContentEntry())) {
        if (Comparing.equal(filePointer.getUrl(), url)) {
          return filePointer;
        }
      }
      return null;
    }

    public void addRoot(PyRootTypeProvider provider, @NotNull final VirtualFilePointer root) {
      provider.getRoots().putValue(getContentEntry(), root);
      fireUpdate();
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
        for (PyRootTypeProvider provider : myRootTypeProviders) {
          MultiMap<ContentEntry, VirtualFilePointer> roots = provider.getRoots();
          if (!roots.get(getContentEntry()).isEmpty()) {
            final JComponent sourcesComponent = createFolderGroupComponent(provider.getName() + " Folders",
                                                                           provider.createFolders(getContentEntry()),
                                                                           provider.getColor(), null);
            this.add(sourcesComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTH,
                                                              GridBagConstraints.HORIZONTAL, new Insets(0, 0, 10, 0), 0, 0));
          }

        }
      }
    }
  }

  private class MyContentEntryTreeEditor extends ContentEntryTreeEditor {

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
      for (PyRootTypeProvider provider : myRootTypeProviders) {
        ContentEntryEditingAction action = provider.createRootEntryEditingAction(myTree, myFilePointersDisposable, PyContentEntriesEditor.this, getModel());
        myEditingActionsGroup.add(action);
        CustomShortcutSet shortcut = provider.getShortcut();
        if (shortcut != null) {
          action.registerCustomShortcutSet(shortcut, myTree);
        }
      }
    }

    @Override
    protected TreeCellRenderer getContentEntryCellRenderer() {
      return new ContentEntryTreeCellRenderer(this, getEditHandlers()) {
        @Override
        protected Icon updateIcon(final ContentEntry entry, final VirtualFile file, final Icon originalIcon) {
          for (PyRootTypeProvider provider : myRootTypeProviders) {
            if (provider.hasRoot(file, PyContentEntriesEditor.this)) {
              return provider.getIcon();
            }
          }
          return super.updateIcon(entry, file, originalIcon);
        }
      };
    }
  }

  @Override
  protected void addAdditionalSettingsToPanel(JPanel mainPanel) {
    mainPanel.add(myWarningPanel.getComponent(), BorderLayout.SOUTH);
  }

  public FacetErrorPanel getWarningPanel() {
    return myWarningPanel;
  }
}
