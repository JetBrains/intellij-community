package org.jetbrains.idea.maven.utils.library;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;

public class RepositoryLibrarySupportInModuleProvider extends FrameworkSupportInModuleProvider {
  @NotNull private RepositoryLibraryDescription libraryDescription;
  @NotNull private FrameworkTypeEx myFrameworkType;

  public RepositoryLibrarySupportInModuleProvider(@NotNull FrameworkTypeEx type,
                                                  @NotNull RepositoryLibraryDescription libraryDescription) {
    myFrameworkType = type;
    this.libraryDescription = libraryDescription;
  }

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return myFrameworkType;
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new RepositoryLibrarySupportInModuleConfigurable(model.getProject(), libraryDescription);
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }
}
