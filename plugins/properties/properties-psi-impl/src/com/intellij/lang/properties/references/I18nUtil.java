/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties.references;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileProcessor;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.xml.XmlPropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class I18nUtil {
  @NotNull
  public static List<PropertiesFile> propertiesFilesByBundleName(final String resourceBundleName, final PsiElement context) {
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

  public static void createProperty(Project project,
                                    Collection<PropertiesFile> propertiesFiles,
                                    String key,
                                    String value) throws IncorrectOperationException {
    createProperty(project, propertiesFiles, key, value, false);
  }

  public static void createProperty(Project project,
                                    Collection<PropertiesFile> propertiesFiles,
                                    String key,
                                    String value,
                                    boolean replaceIfExist) throws IncorrectOperationException {
    for (PropertiesFile file : propertiesFiles) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      documentManager.commitDocument(documentManager.getDocument(file.getContainingFile()));

      IProperty existingProperty = file.findPropertyByKey(key);
      if (existingProperty == null) {
        file.addProperty(key, value);
      }
      else if (replaceIfExist) {
        existingProperty.setValue(value);
      }
    }
  }

  public static List<String> defaultSuggestPropertiesFiles(Project project) {
    final List<String> paths = new ArrayList<>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    PropertiesReferenceManager.getInstance(project).processAllPropertiesFiles((baseName, propertiesFile) -> {
      if (propertiesFile instanceof XmlPropertiesFile) {
        return true;
      }
      VirtualFile virtualFile = propertiesFile.getVirtualFile();
      if (projectFileIndex.isInContent(virtualFile)) {
        String path = FileUtil.toSystemDependentName(virtualFile.getPath());
        paths.add(path);
      }
      return true;
    });
    return paths;
  }
}
