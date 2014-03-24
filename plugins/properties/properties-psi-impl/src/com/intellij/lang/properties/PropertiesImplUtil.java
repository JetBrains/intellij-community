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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class PropertiesImplUtil extends PropertiesUtil {
  public static ResourceBundle getResourceBundle(final PsiFile containingFile) {
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (!containingFile.isValid() || virtualFile == null) {
      return EmptyResourceBundle.getInstance();
    }
    String baseName = getBaseName(virtualFile);
    PsiDirectory directory = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
      @Nullable
      public PsiDirectory compute() {
        return containingFile.getContainingDirectory();
      }});
    if (directory == null) return EmptyResourceBundle.getInstance();

    return new ResourceBundleImpl(directory.getVirtualFile(), baseName);
  }

  public static boolean isPropertiesFile(VirtualFile file, Project project) {
    return getPropertiesFile(PsiManager.getInstance(project).findFile(file)) != null;
  }

  public static boolean isPropertiesFile(PsiFile file) {
    return getPropertiesFile(file) != null;
  }

  @Nullable
  public static PropertiesFile getPropertiesFile(@Nullable PsiFile file) {
    if (file == null) return null;
    return file instanceof PropertiesFile ? (PropertiesFile)file : XmlPropertiesFileImpl.getPropertiesFile(file);
  }



  @NotNull
  public static List<IProperty> findPropertiesByKey(final Project project, final String key) {
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

}
