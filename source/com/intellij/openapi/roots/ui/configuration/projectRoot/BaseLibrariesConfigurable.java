package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

public abstract class BaseLibrariesConfigurable extends BaseStructureConfigurable  {
  protected String myLevel;

  protected BaseLibrariesConfigurable(final Project project) {
    super(project);
  }


  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  public boolean isModified() {
    boolean isModified = false;
    for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
      isModified |= provider.isChanged();
    }

    return isModified;
  }

  public void reset() {
    super.reset();

    myTree.setRootVisible(false);

    reloadTree();
  }

  private void reloadTree() {
    myRoot.removeAllChildren();


    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();

    final LibrariesModifiableModel provider = myContext.myLevel2Providers.get(myLevel);
    createLibrariesNode(registrar.getLibraryTableByLevel(myLevel, myProject), provider,
                                     myContext.createModifiableModelProvider(myLevel, false));
  }


  private void createLibrariesNode(final LibraryTable table,
                                     LibrariesModifiableModel provider,
                                     final LibraryTableModifiableModelProvider modelProvider) {
    provider = new LibrariesModifiableModel(table);
    final Library[] libraries = provider.getLibraries();
    for (Library library : libraries) {
      addNode(new MyNode(new LibraryConfigurable(modelProvider, library, myProject, TREE_UPDATER)), myRoot);
    }
  }

  public void apply() throws ConfigurationException {
    super.apply();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
          provider.deferredCommit();
        }
      }
    });
  }

  public MyNode createLibraryNode(Library library, String presentableName, Module module) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      final String level = table.getTableLevel();
      final LibraryConfigurable configurable =
        new LibraryConfigurable(myContext.createModifiableModelProvider(level, false), library, myProject, TREE_UPDATER);
      final MyNode node = new MyNode(configurable);
      addNode(node, myRoot);
      return node;
    }

    return null;
  }

  public void dispose() {
    myContext.myLevel2Providers.clear();
  }

  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message(getAddText())) {
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        return new AnAction[]{new AnAction(getAddText()) {
          public void actionPerformed(AnActionEvent e) {
            final StructureConfigrableContext context = ProjectStructureConfigurable.getInstance(myProject).getContext();
            final LibraryTableEditor editor = LibraryTableEditor.editLibraryTable(getModelProvider(myContext, false), myProject);
            editor.createAddLibraryAction(true, myTree).actionPerformed(null);
            Disposer.dispose(editor);
          }
        }};
      }
    };
  }

  protected abstract String getAddText();

  protected abstract LibraryTableModifiableModelProvider getModelProvider(StructureConfigrableContext context, final boolean editable);

  protected void removeLibrary(final Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      getModelProvider(myContext, true).getModifiableModel().removeLibrary(library);
      myContext.invalidateModules(myContext.myLibraryDependencyCache.get(library));
      myContext.myLibraryDependencyCache.remove(library);
    }
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a library to view or edit its details here";
  }
}
