/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 */
public class PropertiesUtil {
  public final static Pattern LOCALE_PATTERN = Pattern.compile("(_[a-zA-Z]{2,8}(_[a-zA-Z]{2}|[0-9]{3})?(_[\\w\\-]+)?)\\.[^_]+$");
  public static final Set<Character> BASE_NAME_BORDER_CHAR = ContainerUtil.newHashSet('-', '_', '.');


  /**
   * @deprecated use PropertiesUtil.isPropertyComplete(ResourceBundle resourceBundle, String propertyName)
   */
  @Deprecated
  public static boolean isPropertyComplete(final Project project, final ResourceBundle resourceBundle, final String propertyName) {
    return isPropertyComplete(resourceBundle, propertyName);
  }

  public static boolean isPropertyComplete(final ResourceBundle resourceBundle, final String propertyName) {
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      if (propertiesFile.findPropertyByKey(propertyName) == null) return false;
    }
    return true;
  }

  @NotNull
  public static String getDefaultBaseName(final Collection<PropertiesFile> files) {
    String commonPrefix = null;
    for (PropertiesFile file : files) {
      final String baseName = file.getVirtualFile().getNameWithoutExtension();
      if (commonPrefix == null) {
        commonPrefix = baseName;
      } else {
        commonPrefix = StringUtil.commonPrefix(commonPrefix, baseName);
        if (commonPrefix.isEmpty()) {
          break;
        }
      }
    }
    assert commonPrefix != null;
    if (!commonPrefix.isEmpty() && BASE_NAME_BORDER_CHAR.contains(commonPrefix.charAt(commonPrefix.length() - 1))) {
      commonPrefix = commonPrefix.substring(0, commonPrefix.length() - 1);
    }
    return commonPrefix;
  }

  @NotNull
  public static String getDefaultBaseName(@NotNull final VirtualFile file) {
    final String name = file.getName();
    final Matcher matcher = LOCALE_PATTERN.matcher(name);
    final String baseNameWithExtension;
    if (matcher.find()) {
      final MatchResult matchResult = matcher.toMatchResult();
      final String[] splitted = matchResult.group(1).split("_");
      if (splitted.length > 1) {
        baseNameWithExtension = name.substring(0, matchResult.start(1)) + name.substring(matchResult.end(1));
      }
      else {
        baseNameWithExtension = name;
      }
    }
    else {
      baseNameWithExtension = name;
    }

    return FileUtil.getNameWithoutExtension(baseNameWithExtension);
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

  /**
   * @deprecated use PropertiesUtil.findAllProperties(ResourceBundle resourceBundle, String key)
   */
  @NotNull
  @Deprecated
  public static List<IProperty> findAllProperties(Project project, @NotNull ResourceBundle resourceBundle, String key) {
    List<IProperty> result = new SmartList<IProperty>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public static List<IProperty> findAllProperties(@NotNull ResourceBundle resourceBundle, String key) {
    List<IProperty> result = new SmartList<IProperty>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
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
