// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package de.plushnikov.intellij.plugin.jps;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import de.plushnikov.intellij.plugin.Version;
import de.plushnikov.intellij.plugin.activity.LombokProjectValidatorActivity;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class LombokBuildProcessParametersProvider extends BuildProcessParametersProvider {

  private final Project myProject;

  public LombokBuildProcessParametersProvider(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<String> getVMArguments() {
    if (ProjectSettings.isLombokEnabledInProject(myProject) && LombokProjectValidatorActivity.hasLombokLibrary(myProject)) {
      return CachedValuesManager.getManager(myProject).getCachedValue(myProject,
                                                               () -> {
                                                                 List<String> disableOptions =
                                                                   ReadAction.compute(() -> disableOptimisation())
                                                                   ? Collections.singletonList("-Djps.track.ap.dependencies=false")
                                                                   : Collections.emptyList();
                                                                 return new CachedValueProvider.Result<>(disableOptions, ProjectRootManager.getInstance(myProject));
                                                               });

    }
    return super.getVMArguments();
  }

  private boolean disableOptimisation() {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("lombok.experimental");
    if (aPackage != null) {
      PsiDirectory[] directories = aPackage.getDirectories();
      if (directories.length > 0) {
        List<OrderEntry> entries = ProjectRootManager.getInstance(myProject).getFileIndex().getOrderEntriesForFile(directories[0].getVirtualFile());
        if (!entries.isEmpty()) {
          String lombokVersion = Version.parseLombokVersion(entries.get(0));
          if (lombokVersion != null && Version.compareVersionString(lombokVersion, "1.18.16") < 0) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
