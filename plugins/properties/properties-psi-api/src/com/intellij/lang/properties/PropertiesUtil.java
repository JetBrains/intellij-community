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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author cdr
 */
public class PropertiesUtil {


  public static boolean isPropertyComplete(final Project project, ResourceBundle resourceBundle, String propertyName) {
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(project);
    for (PropertiesFile propertiesFile : propertiesFiles) {
      if (propertiesFile.findPropertyByKey(propertyName) == null) return false;
    }
    return true;
  }

  @NotNull
  public static String getBaseName(@NotNull VirtualFile virtualFile) {
    String name = virtualFile.getNameWithoutExtension();

    List<String> parts = StringUtil.split(name, "_");
    if (parts.size() == 1) return parts.get(0);
    if (parts.size() == 0) return "";

    String baseName = parts.get(0);
    for (int i = 1; i< parts.size(); i++) {
      String part = parts.get(i);
      if (part.length() == 2) {
        break;
      }
      baseName += "_";
      baseName += part;
    }

    return baseName;
  }

  /**
   * messages_en.properties is a parent of the messages_en_US.properties
   */
  @Nullable
  public static PropertiesFile getParent(PropertiesFile file, List<PropertiesFile> candidates) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    String name = virtualFile.getNameWithoutExtension();
    String[] parts = name.split("_");
    if (parts.length == 1) return null;
    List<String> partsList = Arrays.asList(parts);
    for (int i=parts.length-1; i>=1;i--) {
      String parentName = StringUtil.join(partsList.subList(0, i), "_") + "." + virtualFile.getExtension();
      for (PropertiesFile candidate : candidates) {
        if (parentName.equals(candidate.getName())) return candidate;
      }
    }
    return null;
  }

  @Nullable
  public static String getFullName(final PropertiesFile psiFile) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
      public String compute() {
        PsiDirectory directory = psiFile.getParent();
        String packageQualifiedName = getPackageQualifiedName(directory);
        if (packageQualifiedName == null) {
          return null;
        }
        StringBuilder qName = new StringBuilder(packageQualifiedName);
          if (qName.length() > 0) {
            qName.append(".");
          }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        assert virtualFile != null;
        qName.append(getBaseName(virtualFile));
        return qName.toString();
      }
    });
  }

  @NotNull
  public static Locale getLocale(VirtualFile propertiesFile) {
    String name = propertiesFile.getNameWithoutExtension();
    String tail = StringUtil.trimStart(name, getBaseName(propertiesFile));
    tail = StringUtil.trimStart(tail, "_");
    String[] parts = tail.split("_");
    String language = parts.length == 0 ? "" : parts[0];
    String country = "";
    String variant = "";
    if (parts.length >= 2 && parts[1].length() == 2) {
      country = parts[1];
      for (int i = 2; i < parts.length; i++) {
        String part = parts[i];
        if (variant.length() != 0) variant += "_";
        variant += part;
      }
    }

    return new Locale(language,country,variant);
  }

  @NotNull
  public static List<IProperty> findAllProperties(Project project, @NotNull ResourceBundle resourceBundle, String key) {
    List<IProperty> result = new SmartList<IProperty>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(project);
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public static boolean isUnescapedBackSlashAtTheEnd (String text) {
    boolean result = false;
    for (int i = text.length()-1; i>=0; i--) {
      if (text.charAt(i) == '\\') {
        result = !result;
      }
      else {
        break;
      }
    }
    return result;
  }

  @Nullable
  public static String getPackageQualifiedName(@NotNull PsiDirectory directory) {
    return ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getPackageNameByDirectory(directory.getVirtualFile());
  }
}
