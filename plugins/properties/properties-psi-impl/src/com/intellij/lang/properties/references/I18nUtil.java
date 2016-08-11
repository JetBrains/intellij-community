/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  public static void createProperty(final Project project,
                                    final Collection<PropertiesFile> propertiesFiles,
                                    final String key,
                                    final String value) throws IncorrectOperationException {
    for (PropertiesFile file : propertiesFiles) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      documentManager.commitDocument(documentManager.getDocument(file.getContainingFile()));

      IProperty existingProperty = file.findPropertyByKey(key);
      if (existingProperty == null) {
        file.addProperty(key, value);
      }
    }
  }

  public static List<String> defaultSuggestPropertiesFiles(Project project) {
    final List<String> paths = new ArrayList<>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    PropertiesReferenceManager.getInstance(project).processAllPropertiesFiles(new PropertiesFileProcessor() {

      @Override
      public boolean process(String baseName, PropertiesFile propertiesFile) {
        if (propertiesFile instanceof XmlPropertiesFile) {
          return true;
        }
        VirtualFile virtualFile = propertiesFile.getVirtualFile();
        if (projectFileIndex.isInContent(virtualFile)) {
          String path = FileUtil.toSystemDependentName(virtualFile.getPath());
          paths.add(path);
        }
        return true;
      }
    });
    return paths;
  }
}
