// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package de.plushnikov.intellij.plugin.jps;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtilCore;
import de.plushnikov.intellij.plugin.LombokClassNames;
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
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(LombokClassNames.BUILDER, GlobalSearchScope.allScope(myProject));
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
    if (virtualFile != null) {
      List<OrderEntry> entries = ProjectRootManager.getInstance(myProject).getFileIndex().getOrderEntriesForFile(virtualFile);
      if (!entries.isEmpty()) {
        String lombokVersion = Version.parseLombokVersion(entries.get(0));
        if (lombokVersion != null && Version.compareVersionString(lombokVersion, "1.18.16") < 0) {
          return true;
        }
      }
    }
    return false;
  }
}
