/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.extensions.GroovyRunnableScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/29/2016
 */
public class MavenGroovyPomScriptType extends GroovyRunnableScriptType {

  public static final MavenGroovyPomScriptType INSTANCE = new MavenGroovyPomScriptType();

  public MavenGroovyPomScriptType() {
    super("pom");
  }

  @NotNull
  @Override
  public Icon getScriptIcon() {
    return MavenIcons.MavenLogo;
  }

  @Override
  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    return doPatchResolveScope(file, baseScope);
  }

  public GlobalSearchScope doPatchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);

    if (module == null) {
      return baseScope;
    }

    Project project = module.getProject();
    GlobalSearchScope result = baseScope;

    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(file.getProject());
    Boolean hasGroovyModuleLib = cachedValuesManager.getCachedValue(
      file.getProject(), () -> CachedValueProvider.Result.createSingleDependency(
        hasModuleWithGroovyLibrary(project), ProjectRootManagerEx.getInstanceEx(project)));

    if (hasGroovyModuleLib) {
      final Collection<VirtualFile> files = additionalScopeFiles();
      result = result.uniteWith(new NonClasspathDirectoriesScope(files));
    }

    return result;
  }

  public static List<VirtualFile> additionalScopeFiles() {
    VirtualFile jarFile = VfsUtil.findFileByIoFile(GroovyFacetUtil.getBundledGroovyJar(), false);
    if (jarFile != null) {
      VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(jarFile);
      if (jarRoot != null) {
        return Collections.singletonList(jarRoot);
      }
    }
    return ContainerUtil.emptyList();
  }

  private static boolean hasModuleWithGroovyLibrary(@NotNull Project project) {
    Iterator<Library> iterator = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryIterator();
    while (iterator.hasNext()) {
      Library library = iterator.next();
      for (VirtualFile virtualFile : library.getFiles(OrderRootType.CLASSES)) {
        if (GroovyConfigUtils.GROOVY_JAR_PATTERN.matcher(virtualFile.getName()).matches() ||
            GroovyConfigUtils.matchesGroovyAll(virtualFile.getName())) {
          List<OrderEntry> orderEntries = ProjectFileIndex.SERVICE.getInstance(project).getOrderEntriesForFile(virtualFile);
          if (!orderEntries.isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
