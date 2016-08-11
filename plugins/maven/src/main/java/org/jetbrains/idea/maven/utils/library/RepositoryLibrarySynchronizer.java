/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.google.common.base.Predicate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author gregsh
 */
public class RepositoryLibrarySynchronizer implements StartupActivity, DumbAware{
  private static boolean isLibraryNeedToBeReloaded(LibraryEx library, RepositoryLibraryProperties properties) {
    String version = properties.getVersion();
    if (version == null) {
      return false;
    }
    if (version.equals(RepositoryUtils.LatestVersionId)
        || version.equals(RepositoryUtils.ReleaseVersionId)
        || version.endsWith(RepositoryUtils.SnapshotVersionSuffix)) {
      return true;
    }
    for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
      if (library.getFiles(orderRootType).length != library.getUrls(orderRootType).length) {
        return true;
      }
    }
    return false;
  }

  private static Collection<Library> collectLibraries(final @NotNull Project project, final @NotNull Predicate<Library> predicate) {
    final HashSet<Library> result = new HashSet<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      for (final Module module : ModuleManager.getInstance(project).getModules()) {
        OrderEnumerator.orderEntries(module).withoutSdk().forEachLibrary(library -> {
          if (predicate.apply(library)) {
            result.add(library);
          }
          return true;
        });
      }
      for (Library library : ProjectLibraryTable.getInstance(project).getLibraries()) {
        if (predicate.apply(library)) {
          result.add(library);
        }
      }
    });
    return result;
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    StartupManager.getInstance(project).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
          @Override
          public void run() {
            final Collection<Library> libraries = collectLibraries(project, new Predicate<Library>() {
              @Override
              public boolean apply(Library library) {
                if (!(library instanceof LibraryEx)) {
                  return false;
                }
                LibraryEx libraryEx = (LibraryEx)library;
                return libraryEx.getKind() == RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
                       && libraryEx.getProperties() instanceof RepositoryLibraryProperties
                       && isLibraryNeedToBeReloaded(libraryEx, (RepositoryLibraryProperties)libraryEx.getProperties());
              }
            });
            for (Library library : libraries) {
              final LibraryEx libraryEx = (LibraryEx)library;
              RepositoryUtils.reloadDependencies(project, libraryEx);
            }
          }
        }, project.getDisposed());
      }
    });
  }
}
