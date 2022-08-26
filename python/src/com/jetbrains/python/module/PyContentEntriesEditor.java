// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module;

import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.*;
import com.intellij.openapi.roots.ui.configuration.actions.ContentEntryEditingAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class PyContentEntriesEditor extends CommonContentEntriesEditor {
  private final List<PyRootTypeProvider> myRootTypeProviders;
  private final Module myModule;
  private Disposable myFilePointersDisposable;
  private MyContentEntryEditor myContentEntryEditor;
  private final FacetErrorPanel myWarningPanel;

  public PyContentEntriesEditor(Module module, ModuleConfigurationState moduleConfigurationState,
                                boolean withBorders, JpsModuleSourceRootType<?>... rootTypes) {
    super(module.getName(), moduleConfigurationState, withBorders, rootTypes);
    myRootTypeProviders = PyRootTypeProvider.EP_NAME.getExtensionList();
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
    addContentEntryPanels(entries.toArray(new ContentEntry[0]));
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
    public void deleteContentFolder(ContentEntry contentEntry, ContentFolderRef folderRef) {
      if (folderRef instanceof ExternalContentFolderRef) {
        String url = folderRef.getUrl();
        for (PyRootTypeProvider provider : myRootTypeProviders) {
          Collection<VirtualFilePointer> roots = provider.getRoots().get(contentEntry);
          if (roots.stream().anyMatch(pointer -> pointer.getUrl().equals(url))) {
            removeRoot(contentEntry, url, provider);
            return;
          }
        }
      }
      super.deleteContentFolder(contentEntry, folderRef);
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
        if (Objects.equals(filePointer.getUrl(), url)) {
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
      protected ContentEntry getContentEntry() {
        //noinspection ConstantConditions
        return MyContentEntryEditor.this.getContentEntry();
      }

      @Override
      protected void addFolderGroupComponents() {
        super.addFolderGroupComponents();
        for (PyRootTypeProvider provider : myRootTypeProviders) {
          MultiMap<ContentEntry, VirtualFilePointer> roots = provider.getRoots();
          Collection<VirtualFilePointer> pointers = roots.get(getContentEntry());
          if (!pointers.isEmpty()) {
            List<ExternalContentFolderRef> folderRefs = ContainerUtil.map(pointers, ExternalContentFolderRef::new);
            final JComponent sourcesComponent = createFolderGroupComponent(provider.getRootsGroupTitle(),
                                                                           folderRefs,
                                                                           provider.getRootsGroupColor(), null);
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

    MyContentEntryTreeEditor(Project project, List<ModuleSourceRootEditHandler<?>> handlers) {
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
    protected TreeCellRenderer getContentEntryCellRenderer(@NotNull ContentEntry contentEntry) {
      return new ContentEntryTreeCellRenderer(this, contentEntry, getEditHandlers()) {
        @Override
        protected Icon updateIcon(final ContentEntry entry, final VirtualFile file, final Icon originalIcon) {
          for (PyRootTypeProvider provider : myRootTypeProviders) {
            if (provider.hasRoot(file, PyContentEntriesEditor.this)) {
              return provider.getIcon();
            }
          }
          // JavaModuleSourceRootEditHandler gives every directory under a source root a package icon.
          // Since we use the same icon for explicit namespace package "roots", we forcibly replace icons
          // for other "false" packages with the one for a plain directory to avoid confusion.
          Icon defaultIcon = super.updateIcon(entry, file, originalIcon);
          if (defaultIcon == AllIcons.Nodes.Package) {
            return PlatformIcons.FOLDER_ICON;
          }
          return defaultIcon;
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
