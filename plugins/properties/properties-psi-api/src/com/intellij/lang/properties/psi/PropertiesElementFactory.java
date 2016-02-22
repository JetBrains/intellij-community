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

package com.intellij.lang.properties.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author cdr
 */
public class PropertiesElementFactory {
  private static final UserDataCache<PropertiesFile,Project,Void> PROPERTIES = new UserDataCache<PropertiesFile, Project, Void>("system.properties.file") {

    protected PropertiesFile compute(Project project, Void p) {
      return createPropertiesFile(project, System.getProperties(), "system");
    }
  };

  @NotNull
  public static IProperty createProperty(@NotNull Project project,
                                         @NonNls @NotNull String name,
                                         @NonNls @NotNull String value,
                                         @Nullable Character delimiter) {
    String text = getPropertyText(name, value, delimiter, project, true);
    final PropertiesFile dummyFile = createPropertiesFile(project, text);
    return dummyFile.getProperties().get(0);
  }

  @Deprecated
  @NotNull
  public static IProperty createProperty(@NotNull Project project,
                                         @NonNls @NotNull String name,
                                         @NonNls @NotNull String value) {
    return createProperty(project, name, value, null);
  }

  @NotNull
  public static String getPropertyText(@NonNls @NotNull String name,
                                       @NonNls @NotNull String value,
                                       @NonNls @Nullable Character delimiter,
                                       @Nullable Project project,
                                       boolean escape) {
    if (delimiter == null) {
      delimiter = project == null ? '=' : PropertiesCodeStyleSettings.getInstance(project).getDelimiter();
    }
    return (escape ? escape(name) : name) + String.valueOf(delimiter) + (escape ? escapeValue(value, delimiter) : value);
  }

  @NotNull
  public static PropertiesFile createPropertiesFile(@NotNull Project project, @NonNls @NotNull String text) {
    @NonNls String filename = "dummy." + PropertiesFileType.INSTANCE.getDefaultExtension();
    return (PropertiesFile)PsiFileFactory.getInstance(project)
      .createFileFromText(filename, PropertiesFileType.INSTANCE, text);
  }

  @NotNull
  public static PropertiesFile createPropertiesFile(@NotNull Project project, Properties properties, String fileName) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      properties.store(stream, "");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    @NonNls String filename = fileName + "." + PropertiesFileType.INSTANCE.getDefaultExtension();
    return (PropertiesFile)PsiFileFactory.getInstance(project)
      .createFileFromText(filename, PropertiesFileType.INSTANCE, stream.toString());
  }

  @NotNull
  public static PropertiesFile getSystemProperties(@NotNull Project project) {
    return PROPERTIES.get(project, null);
  }

  @NotNull
  private static String escape(@NotNull String name) {
    if (StringUtil.startsWithChar(name, '#') || StringUtil.startsWithChar(name, '!')) {
      name = "\\" + name;
    }
    return StringUtil.escapeChars(name, '=', ':', ' ', '\t');
  }

  @Deprecated
  public static String escapeValue(String value) {
    return escapeValue(value, '=');
  }

  public static String escapeValue(String value, char delimiter) {
    return PropertiesResourceBundleUtil.fromValueEditorToPropertyValue(value, delimiter);
  }
}
