/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.xml.XmlPropertiesIndex;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;

/**
 * @author max
 */
public class PropertiesReferenceManager {
  private final PsiManager myPsiManager;
  private final DumbService myDumbService;

  public static PropertiesReferenceManager getInstance(Project project) {
    return ServiceManager.getService(project, PropertiesReferenceManager.class);
  }

  public PropertiesReferenceManager(PsiManager psiManager, DumbService dumbService) {
    myPsiManager = psiManager;
    myDumbService = dumbService;
  }

  @NotNull
  public List<PropertiesFile> findPropertiesFiles(@NotNull final Module module, final String bundleName) {
    ConcurrentMap<String, List<PropertiesFile>> map =
      CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
        ConcurrentMap<String, List<PropertiesFile>> factoryMap = ConcurrentFactoryMap.createMap(
          bundleName1 -> findPropertiesFiles(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module), bundleName1,
                                             BundleNameEvaluator.DEFAULT));
        return CachedValueProvider.Result.create(factoryMap, PsiModificationTracker.MODIFICATION_COUNT);
      });
    return map.get(bundleName);
  }

  @NotNull
  public List<PropertiesFile> findPropertiesFiles(@NotNull final GlobalSearchScope searchScope,
                                                  final String bundleName,
                                                  BundleNameEvaluator bundleNameEvaluator) {


    final ArrayList<PropertiesFile> result = new ArrayList<>();
    processPropertiesFiles(searchScope, (baseName, propertiesFile) -> {
      if (baseName.equals(bundleName)) {
        result.add(propertiesFile);
      }
      return true;
    }, bundleNameEvaluator);
    return result;
  }

  @Nullable
  public PropertiesFile findPropertiesFile(final Module module,
                                           final String bundleName,
                                           final Locale locale) {
    List<PropertiesFile> propFiles = findPropertiesFiles(module, bundleName);
    if (locale != null) {
      for(PropertiesFile propFile: propFiles) {
        if (propFile.getLocale().equals(locale)) {
          return propFile;
        }
      }
    }

    // fallback to default locale
    for(PropertiesFile propFile: propFiles) {
      if (propFile.getLocale().getLanguage().length() == 0 || propFile.getLocale().equals(Locale.getDefault())) {
        return propFile;
      }
    }

    // fallback to any file
    if (!propFiles.isEmpty()) {
      return propFiles.get(0);
    }

    return null;
  }

  public boolean processAllPropertiesFiles(@NotNull final PropertiesFileProcessor processor) {
    return processPropertiesFiles(GlobalSearchScope.allScope(myPsiManager.getProject()), processor, BundleNameEvaluator.DEFAULT);
  }

  public boolean processPropertiesFiles(@NotNull final GlobalSearchScope searchScope,
                                        @NotNull final PropertiesFileProcessor processor,
                                        @NotNull final BundleNameEvaluator evaluator) {
    for(VirtualFile file:FileTypeIndex.getFiles(PropertiesFileType.INSTANCE, searchScope)) {
      if (!processFile(file, evaluator, processor)) return false;
    }
    if (!myDumbService.isDumb()) {
      for(VirtualFile file:FileBasedIndex.getInstance().getContainingFiles(XmlPropertiesIndex.NAME, XmlPropertiesIndex.MARKER_KEY, searchScope)) {
        if (!processFile(file, evaluator, processor)) return false;
      }
    }

    return true;
  }

  private boolean processFile(VirtualFile file, BundleNameEvaluator evaluator, PropertiesFileProcessor processor) {
    final PsiFile psiFile = myPsiManager.findFile(file);
    PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    if (propertiesFile != null) {
      final String qName = evaluator.evaluateBundleName(psiFile);
      if (qName != null) {
        if (!processor.process(qName, propertiesFile)) return false;
      }
    }
    return true;
  }
}
