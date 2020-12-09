// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.references;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.BundleNameEvaluator;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileProcessor;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.xml.XmlPropertiesFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class I18nUtil {
  @NotNull
  public static List<PropertiesFile> propertiesFilesByBundleName(@Nullable String resourceBundleName, @NotNull PsiElement context) {
    if (resourceBundleName == null) return Collections.emptyList();
    PsiFile containingFile = context.getContainingFile();
    PsiElement containingFileContext = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
    if (containingFileContext != null) containingFile = containingFileContext.getContainingFile();

    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) {
      virtualFile = containingFile.getOriginalFile().getVirtualFile();
    }
    if (virtualFile != null) {
      Project project = containingFile.getProject();
      final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(virtualFile);
      if (module != null) {
        PropertiesReferenceManager refManager = PropertiesReferenceManager.getInstance(project);
        return refManager.findPropertiesFiles(module, resourceBundleName);
      }
    }
    return Collections.emptyList();
  }

  public static void createProperty(@NotNull Project project,
                                    @NotNull Collection<? extends PropertiesFile> propertiesFiles,
                                    @NotNull String key,
                                    @NotNull String value) throws IncorrectOperationException {
    createProperty(project, propertiesFiles, key, value, false);
  }

  public static void createProperty(@NotNull Project project,
                                    @NotNull Collection<? extends PropertiesFile> propertiesFiles,
                                    @NotNull String key,
                                    @NotNull String value,
                                    boolean replaceIfExist) throws IncorrectOperationException {
    for (PropertiesFile file : propertiesFiles) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      Document document = documentManager.getDocument(file.getContainingFile());
      if (document != null) {
        documentManager.commitDocument(document);
      }

      IProperty existingProperty = file.findPropertyByKey(key);
      if (existingProperty == null) {
        file.addProperty(key, value, PropertyKeyValueFormat.MEMORY);
      }
      else if (replaceIfExist) {
        existingProperty.setValue(value, PropertyKeyValueFormat.MEMORY);
      }
    }
  }

  public static @NotNull List<String> defaultSuggestPropertiesFiles(@NotNull Project project,
                                                                    @NotNull Set<? extends Module> contextModules) {
    List<String> relevantPaths = new ArrayList<>();
    List<String> otherPaths = new ArrayList<>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    PropertiesFileProcessor processor = (baseName, propertiesFile) -> {
      if (propertiesFile instanceof XmlPropertiesFile) {
        return true;
      }
      VirtualFile virtualFile = propertiesFile.getVirtualFile();
      if (projectFileIndex.isInContent(virtualFile)) {
        String path = FileUtil.toSystemDependentName(virtualFile.getPath());
        Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
        boolean relevant = contextModules.contains(module);
        (relevant ? relevantPaths : otherPaths).add(path);
      }
      return true;
    };

    if (contextModules.isEmpty()) {
      PropertiesReferenceManager.getInstance(project).processAllPropertiesFiles(processor);
    }
    else {
      GlobalSearchScope scope = GlobalSearchScope.union(ContainerUtil.map(contextModules, Module::getModuleWithDependenciesScope));
      PropertiesReferenceManager.getInstance(project).processPropertiesFiles(scope, processor, BundleNameEvaluator.DEFAULT);
    }
    Collections.sort(relevantPaths);
    Collections.sort(otherPaths);
    relevantPaths.addAll(otherPaths);
    return relevantPaths;
  }
}
