package org.jetbrains.idea.maven.utils.library;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.ProjectBundle;

import javax.swing.*;
import java.util.Arrays;

public class RepositoryLibrarySupportInModuleConfigurable extends FrameworkSupportInModuleConfigurable {
  @NotNull private final RepositoryLibraryDescription libraryDescription;
  @Nullable private final Project project;
  private RepositoryLibraryPropertiesEditor editor;

  public RepositoryLibrarySupportInModuleConfigurable(@Nullable Project project, @NotNull RepositoryLibraryDescription libraryDescription) {
    this.libraryDescription = libraryDescription;
    this.project = project;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (editor == null) {
      editor = new RepositoryLibraryPropertiesEditor(project, false, false, libraryDescription.createDefaultProperties());
    }
    return editor.getMainPanel();
  }

  public boolean showEditorAndGet() {
    if (editor == null) {
      editor = new RepositoryLibraryPropertiesEditor(project, false, false, libraryDescription.createDefaultProperties());
    }
    editor.setTitle(ProjectBundle.message("maven.add.library.support", libraryDescription.getDisplayName()));
    editor.init();
    return editor.showAndGet();
  }

  @Override
  public void addSupport(@NotNull Module module,
                         @NotNull final ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider) {
    LibraryTable.ModifiableModel modifiableModel = modifiableModelsProvider.getLibraryTableModifiableModel(module.getProject());

    Library library = Iterables.find(Arrays.asList(modifiableModel.getLibraries()), new Predicate<Library>() {
      @Override
      public boolean apply(@Nullable Library library) {
        return isLibraryEqualsToSelected(library);
      }
    }, null);
    if (library == null) {
      library = createNewLibrary(module, modifiableModel);
    }
    final DependencyScope dependencyScope = LibraryDependencyScopeSuggester.getDefaultScope(library);
    final ModifiableRootModel moduleModifiableModel = modifiableModelsProvider.getModuleModifiableModel(module);
    LibraryOrderEntry foundEntry =
      (LibraryOrderEntry)Iterables.find(Arrays.asList(moduleModifiableModel.getOrderEntries()), new Predicate<OrderEntry>() {
        @Override
        public boolean apply(@Nullable OrderEntry entry) {
          return entry instanceof LibraryOrderEntry
                 && ((LibraryOrderEntry)entry).getScope() == dependencyScope
                 && isLibraryEqualsToSelected(((LibraryOrderEntry)entry).getLibrary());
        }
      }, null);
    if (foundEntry == null) {
      rootModel.addLibraryEntry(library).setScope(dependencyScope);
    }
  }

  private LibraryEx createNewLibrary(@NotNull final Module module, final LibraryTable.ModifiableModel modifiableModel) {
    final LibraryEx library = (LibraryEx)modifiableModel.createLibrary(
      LibraryEditingUtil.suggestNewLibraryName(modifiableModel, RepositoryLibraryType.getInstance().getDescription(editor.getProperties())),
      RepositoryLibraryType.REPOSITORY_LIBRARY_KIND);
    RepositoryLibraryProperties libraryProperties = (RepositoryLibraryProperties)library.getProperties();
    libraryProperties.setMavenId(editor.getProperties().getMavenId());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifiableModel.commit();
      }
    });
    Task task = new Task.Backgroundable(project, "Maven", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        RepositoryUtils.loadDependencies(
          indicator,
          module.getProject(),
          library,
          editor.downloadSources(),
          editor.downloadJavaDocs());
      }
    };
    ProgressManager.getInstance().run(task);

    return library;
  }

  private boolean isLibraryEqualsToSelected(Library library) {
    if (!(library instanceof LibraryEx)) {
      return false;
    }

    LibraryEx libraryEx = (LibraryEx)library;
    if (!RepositoryLibraryType.REPOSITORY_LIBRARY_KIND.equals(libraryEx.getKind())) {
      return false;
    }

    LibraryProperties libraryProperties = libraryEx.getProperties();
    if (libraryProperties == null || !(libraryProperties instanceof RepositoryLibraryProperties)) {
      return false;
    }
    RepositoryLibraryProperties repositoryLibraryProperties = (RepositoryLibraryProperties)libraryProperties;
    RepositoryLibraryDescription description = RepositoryLibraryDescription.findDescription(repositoryLibraryProperties);

    if (!description.equals(libraryDescription)) {
      return false;
    }

    return Comparing.equal(repositoryLibraryProperties.getVersion(), editor.getSelectedVersion());
  }
}
