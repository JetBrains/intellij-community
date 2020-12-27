// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package de.plushnikov.intellij.plugin.jps;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import de.plushnikov.intellij.plugin.Version;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class LombokBuildProcessParametersProvider extends BuildProcessParametersProvider {
  private static final Logger LOG = Logger.getInstance(LombokBuildProcessParametersProvider.class);

  private final Project myProject;

  public LombokBuildProcessParametersProvider(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<String> getVMArguments() {
    if (isVersionLessThan1_18_16(myProject)) {
      return Collections.singletonList("-Djps.track.ap.dependencies=false");
    }
    return super.getVMArguments();
  }

  private boolean isVersionLessThan1_18_16(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Boolean isVersionLessThan;
      try {
        isVersionLessThan = ReadAction.nonBlocking(
          () -> isVersionLessThanInternal(project, Version.LAST_LOMBOK_VERSION_WITH_JPS_FIX))
          .executeSynchronously();
      } catch (ProcessCanceledException e) {
        throw e;
      } catch (Throwable e) {
        isVersionLessThan = false;
        LOG.error(e);
      }
      return new CachedValueProvider.Result<>(isVersionLessThan, ProjectRootManager.getInstance(project));
    });
  }

  private boolean isVersionLessThanInternal(@NotNull Project project, @NotNull String version) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("lombok.experimental");
    if (aPackage != null) {
      PsiDirectory[] directories = aPackage.getDirectories();
      if (directories.length > 0) {
        List<OrderEntry> entries =
          ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(directories[0].getVirtualFile());
        if (!entries.isEmpty()) {
          return Version.isLessThan(entries.get(0), version);
        }
      }
    }
    return false;
  }
}
