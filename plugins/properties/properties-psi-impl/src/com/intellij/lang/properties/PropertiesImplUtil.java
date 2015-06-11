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
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.lang.properties.xml.XmlPropertiesFileImpl;
import com.intellij.lang.properties.xml.XmlPropertiesIndex;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class PropertiesImplUtil extends PropertiesUtil {

  @Nullable
  public static ResourceBundle getResourceBundle(@NotNull final PropertiesFile representative) {
    final PsiFile containingFile = representative.getContainingFile();
    if (!containingFile.isValid()) {
      return EmptyResourceBundle.getInstance();
    }
    final ResourceBundleManager manager = ResourceBundleManager.getInstance(representative.getProject());
    final CustomResourceBundle customResourceBundle =
      manager.getCustomResourceBundle(representative);
    if (customResourceBundle != null) {
      return customResourceBundle;
    }

    final VirtualFile virtualFile = representative.getVirtualFile();
    if (virtualFile == null) {
      return EmptyResourceBundle.getInstance();
    }
    if (manager.isDefaultDissociated(virtualFile)) {
      return new ResourceBundleImpl(representative);
    }


    final String baseName = manager.getBaseName(containingFile);
    final PsiDirectory directory = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
      @Nullable
      public PsiDirectory compute() {
        return containingFile.getContainingDirectory();
      }});
    if (directory == null) return EmptyResourceBundle.getInstance();
    final ResourceBundle bundle = getResourceBundle(baseName, directory);
    return bundle == null ? new ResourceBundleImpl(representative) : bundle;
  }

  @Nullable
  private static ResourceBundle getResourceBundle(@NotNull final String baseName, @NotNull final PsiDirectory baseDirectory) {
    PropertiesFile defaultPropertiesFile = null;
    final ResourceBundleManager bundleBaseNameManager = ResourceBundleManager.getInstance(baseDirectory.getProject());
    final PsiFile[] psiFiles = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
      @Override
      public PsiFile[] compute() {
        return baseDirectory.getFiles();
      }
    });
    for (final PsiFile psiFile : psiFiles) {
      final PropertiesFile propertiesFile = getPropertiesFile(psiFile);
      if (propertiesFile == null) {
        continue;
      }
      if (baseName.equals(bundleBaseNameManager.getBaseName(psiFile))) {
        if (defaultPropertiesFile == null) {
          defaultPropertiesFile = propertiesFile;
        } else {
          final int nameDiff = FileUtil.getNameWithoutExtension(defaultPropertiesFile.getName()).compareTo(FileUtil.getNameWithoutExtension(propertiesFile.getName()));
          if (nameDiff > 0) {
            defaultPropertiesFile = propertiesFile;
          } else if (nameDiff == 0) {
            //means 2 default properties files
            return null;
          }
        }
      }
    }
    if (defaultPropertiesFile == null) {
      return null;
    }
    return new ResourceBundleImpl(defaultPropertiesFile);
  }

  public static boolean isPropertiesFile(@NotNull VirtualFile file, @NotNull Project project) {
    return getPropertiesFile(PsiManager.getInstance(project).findFile(file)) != null;
  }

  public static boolean isPropertiesFile(@Nullable PsiFile file) {
    return getPropertiesFile(file) != null;
  }

  @Nullable
  public static PropertiesFile getPropertiesFile(@Nullable PsiFile file) {
    if (file == null) return null;
    return file instanceof PropertiesFile ? (PropertiesFile)file : XmlPropertiesFileImpl.getPropertiesFile(file);
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
      new ArrayList<IProperty>(PropertyKeyIndex.getInstance().get(key, project, scope));
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    FileBasedIndex.getInstance().processValues(XmlPropertiesIndex.NAME, new XmlPropertiesIndex.Key(key), null, new FileBasedIndex.ValueProcessor<String>() {
      @Override
      public boolean process(VirtualFile file, String value) {
        if (files.add(file)) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          PropertiesFile propertiesFile = XmlPropertiesFileImpl.getPropertiesFile(psiFile);
          if (propertiesFile != null) {
            properties.addAll(propertiesFile.findPropertiesByKey(key));
          }
        }
        return false;
      }
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
    return getResourceBundle(baseName, baseDirectory);
  }

  public static boolean isAlphaSorted(final Collection<? extends IProperty> properties) {
    String previousKey = null;
    for (IProperty property : properties) {
      final String key = property.getKey();
      if (key == null) {
        return false;
      }
      if (previousKey != null && previousKey.compareTo(key) > 0) {
        return false;
      }
      previousKey = key;
    }
    return true;
  }
}