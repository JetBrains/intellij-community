package org.jetbrains.idea.maven.utils.library;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.IdeaModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.ProjectBundle;

public class RepositoryAddLibraryAction extends IntentionAndQuickFixAction {
  private final Module module;
  @NotNull private final RepositoryLibraryDescription libraryDescription;

  public RepositoryAddLibraryAction(Module module, @NotNull RepositoryLibraryDescription libraryDescription) {
    this.module = module;
    this.libraryDescription = libraryDescription;
  }

  @NotNull
  @Override
  public String getName() {
    return ProjectBundle.message("maven.add.to.module.dependencies", libraryDescription.getDisplayName());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return ProjectBundle.message("maven.library.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
    RepositoryLibrarySupportInModuleConfigurable
      mavenSupport = new RepositoryLibrarySupportInModuleConfigurable(module.getProject(), libraryDescription);
    if (mavenSupport.showEditorAndGet()) {
      IdeaModifiableModelsProvider modifiableModelsProvider = new IdeaModifiableModelsProvider();
      final ModifiableRootModel modifiableModel = modifiableModelsProvider.getModuleModifiableModel(module);
      assert modifiableModel != null;
      mavenSupport.addSupport(
        module,
        modifiableModel,
        modifiableModelsProvider);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          modifiableModel.commit();
        }
      });
    }
  }
}
