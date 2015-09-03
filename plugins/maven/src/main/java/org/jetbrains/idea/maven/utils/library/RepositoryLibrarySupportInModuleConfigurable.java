package org.jetbrains.idea.maven.utils.library;

import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesEditor;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import javax.swing.*;

public class RepositoryLibrarySupportInModuleConfigurable extends FrameworkSupportInModuleConfigurable {
  @NotNull private final RepositoryLibraryDescription libraryDescription;
  @Nullable private final Project project;
  private RepositoryLibraryPropertiesEditor editor;
  private RepositoryLibraryPropertiesModel model;

  public RepositoryLibrarySupportInModuleConfigurable(@Nullable Project project, @NotNull RepositoryLibraryDescription libraryDescription) {
    this.libraryDescription = libraryDescription;
    this.project = project;
    RepositoryLibraryProperties defaultProperties = libraryDescription.createDefaultProperties();
    this.model = new RepositoryLibraryPropertiesModel(defaultProperties.getVersion(), false, false);
    editor = new RepositoryLibraryPropertiesEditor(project, model, libraryDescription);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return editor.getMainPanel();
  }

  @Override
  public void addSupport(@NotNull Module module,
                         @NotNull ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider) {
    RepositoryLibrarySupport librarySupport = new RepositoryLibrarySupport(
      project == null ? ProjectManager.getInstance().getDefaultProject() : project,
      libraryDescription,
      model);
    librarySupport.addSupport(module, rootModel, modifiableModelsProvider);
  }
}
