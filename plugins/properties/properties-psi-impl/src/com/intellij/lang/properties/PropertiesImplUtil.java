/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.lang.properties.xml.XmlPropertiesFileImpl;
import com.intellij.lang.properties.xml.XmlPropertiesIndex;
import com.intellij.lang.properties.xml.XmlProperty;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
public class PropertiesImplUtil extends PropertiesUtil {

  @NotNull
  public static ResourceBundleWithCachedFiles getResourceBundleWithCachedFiles(@NotNull final PropertiesFile representative) {
    return ReadAction.compute(() -> {
      final PsiFile containingFile = representative.getContainingFile();
      if (!containingFile.isValid()) {
        return ResourceBundleWithCachedFiles.EMPTY;
      }
      final ResourceBundleManager manager = ResourceBundleManager.getInstance(representative.getProject());
      final CustomResourceBundle customResourceBundle =
        manager.getCustomResourceBundle(representative);
      if (customResourceBundle != null) {
        return new ResourceBundleWithCachedFiles(customResourceBundle, customResourceBundle.getPropertiesFiles());
      }

      final VirtualFile virtualFile = representative.getVirtualFile();
      if (virtualFile == null) {
        return ResourceBundleWithCachedFiles.EMPTY;
      }
      if (manager.isDefaultDissociated(virtualFile)) {
        return new ResourceBundleWithCachedFiles(new ResourceBundleImpl(representative), Collections.singletonList(representative));
      }


      final String baseName = manager.getBaseName(containingFile);
      final String extension = containingFile.getVirtualFile().getExtension();
      final PsiDirectory directory = containingFile.getContainingDirectory();
      if (directory == null) return ResourceBundleWithCachedFiles.EMPTY;
      final ResourceBundleWithCachedFiles bundle = getResourceBundle(baseName, extension, directory);
      return bundle == null
             ? new ResourceBundleWithCachedFiles(new ResourceBundleImpl(representative), Collections.singletonList(representative))
             : bundle;
    });
  }

  @NotNull
  public static List<PropertiesFile> getResourceBundleFiles(@NotNull PropertiesFile representative) {
    return getResourceBundleWithCachedFiles(representative).getFiles();
  }

  @NotNull
  public static ResourceBundle getResourceBundle(@NotNull PropertiesFile representative) {
    return getResourceBundleWithCachedFiles(representative).getBundle();
  }

  @Nullable
  private static ResourceBundleWithCachedFiles getResourceBundle(@NotNull final String baseName,
                                                                 @Nullable final String extension,
                                                                 @NotNull final PsiDirectory baseDirectory) {
    final ResourceBundleManager bundleBaseNameManager = ResourceBundleManager.getInstance(baseDirectory.getProject());
    final List<PropertiesFile> bundleFiles = Stream
      .of(baseDirectory.isValid() ? baseDirectory.getFiles() : PsiFile.EMPTY_ARRAY)
      .filter(f -> isPropertiesFile(f) &&
                   Comparing.strEqual(f.getVirtualFile().getExtension(), extension) &&
                   Comparing.equal(bundleBaseNameManager.getBaseName(f), baseName))
      .map(PropertiesImplUtil::getPropertiesFile)
      .collect(Collectors.toList());
    if (bundleFiles.isEmpty()) return null;
    return new ResourceBundleWithCachedFiles(new ResourceBundleImpl(bundleFiles.get(0)), bundleFiles);
  }

  public static boolean isPropertiesFile(@Nullable PsiFile file) {
    return getPropertiesFile(file) != null;
  }

  @Nullable
  public static PropertiesFile getPropertiesFile(@NotNull VirtualFile file, @NotNull Project project) {
    return getPropertiesFile(PsiManager.getInstance(project).findFile(file));
  }

  @Contract("null -> null")
  @Nullable
  public static PropertiesFile getPropertiesFile(@Nullable PsiFile file) {
    if (!canBePropertyFile(file)) return null;
    return file instanceof PropertiesFile ? (PropertiesFile)file : XmlPropertiesFileImpl.getPropertiesFile(file);
  }

  public static boolean canBePropertyFile(PsiFile file) {
    return file instanceof PropertiesFile || file instanceof XmlFile && file.getFileType() == StdFileTypes.XML;
  }

  @Nullable
  public static PropertiesFile getPropertiesFile(@Nullable PsiElement element) {
    if (!(element instanceof PsiFile)) return null;
    return getPropertiesFile((PsiFile)element);
  }

  @NotNull
  public static List<IProperty> findPropertiesByKey(@NotNull final Project project, @NotNull final String key) {
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final ArrayList<IProperty> properties =
      new ArrayList<>(PropertyKeyIndex.getInstance().get(key, project, scope));
    final Set<VirtualFile> files = new HashSet<>();
    FileBasedIndex.getInstance().processValues(XmlPropertiesIndex.NAME, new XmlPropertiesIndex.Key(key), null, (file, value) -> {
      if (files.add(file)) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
          PropertiesFile propertiesFile = XmlPropertiesFileImpl.getPropertiesFile(psiFile);
          if (propertiesFile != null) {
            properties.addAll(propertiesFile.findPropertiesByKey(key));
          }
        }
      }
      return true;
    }, scope);
    return properties;
  }

  @Nullable
  public static ResourceBundle createByUrl(final @NotNull String url, final @NotNull Project project) {
    final int idx = url.lastIndexOf('/');
    if (idx == -1) return null;
    final String baseDirectoryName = url.substring(0, idx);
    final String baseName = url.substring(idx + 1);
    final VirtualFile baseDirectoryVirtualFile = VirtualFileManager.getInstance().findFileByUrl(baseDirectoryName);
    if (baseDirectoryVirtualFile == null) {
      return null;
    }
    final PsiDirectory baseDirectory = PsiManager.getInstance(project).findDirectory(baseDirectoryVirtualFile);
    if (baseDirectory == null) {
      return null;
    }
    final ResourceBundleManager bundleBaseNameManager = ResourceBundleManager.getInstance(project);

    for (PsiFile file : baseDirectory.getFiles()) {
      final PropertiesFile propertiesFile = getPropertiesFile(file);
      if (propertiesFile == null) continue;
      final String currBaseName = bundleBaseNameManager.getBaseName(file);
      if (currBaseName.equals(baseName)) {
        return getResourceBundle(propertiesFile);
      }
    }
    return null;
  }

  public static boolean isAlphaSorted(final Collection<? extends IProperty> properties) {
    String previousKey = null;
    for (IProperty property : properties) {
      final String key = property.getKey();
      if (key == null) {
        return false;
      }
      if (previousKey != null && String.CASE_INSENSITIVE_ORDER.compare(previousKey, key) > 0) {
        return false;
      }
      previousKey = key;
    }
    return true;
  }

  @Nullable
  public static IProperty getProperty(@Nullable PsiElement element) {
    if (element instanceof IProperty) {
      return (IProperty)element;
    }
    if (element instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof XmlProperty) {
        return (IProperty)target;
      }
    }
    return null;
  }

  public static class ResourceBundleWithCachedFiles {
    private static final ResourceBundleWithCachedFiles EMPTY =
      new ResourceBundleWithCachedFiles(EmptyResourceBundle.getInstance(), Collections.emptyList());

    private final ResourceBundle myBundle;
    private final List<PropertiesFile> myFiles;

    private ResourceBundleWithCachedFiles(ResourceBundle bundle, List<PropertiesFile> files) {
      myBundle = bundle;
      myFiles = files;
    }

    public ResourceBundle getBundle() {
      return myBundle;
    }

    public List<PropertiesFile> getFiles() {
      return myFiles;
    }
  }
}