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

package com.intellij.lang.properties.psi;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  public static Property createProperty(@NotNull Project project, @NonNls @NotNull String name, @NonNls @NotNull String value) {
    String text = escape(name) + "=" + escapeValue(value);
    final PropertiesFile dummyFile = createPropertiesFile(project, text);
    return dummyFile.getProperties().get(0);
  }

  @NotNull
  public static PropertiesFile createPropertiesFile(@NotNull Project project, @NonNls @NotNull String text) {
    @NonNls String filename = "dummy." + StdFileTypes.PROPERTIES.getDefaultExtension();
    return (PropertiesFile)PsiFileFactory.getInstance(project)
      .createFileFromText(filename, StdFileTypes.PROPERTIES, text);
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
    @NonNls String filename = fileName + "." + StdFileTypes.PROPERTIES.getDefaultExtension();
    return (PropertiesFile)PsiFileFactory.getInstance(project)
      .createFileFromText(filename, StdFileTypes.PROPERTIES, stream.toString());
  }

  @NotNull
  public static PropertiesFile getSystemProperties(@NotNull Project project) {
    return PROPERTIES.get(project, null);
  }

  @NotNull
  private static String escape(@NotNull String name) {
    if (StringUtil.startsWithChar(name, '#')) {
      name = escapeChar(name, '#');
    }
    if (StringUtil.startsWithChar(name, '!')) {
      name = escapeChar(name, '!');
    }
    name = escapeChar(name, '=');
    name = escapeChar(name, ':');
    name = escapeChar(name, ' ');
    name = escapeChar(name, '\t');
    return name;
  }

  @NotNull
  private static String escapeChar(@NotNull String name, char c) {
    int offset = 0;
    while (true) {
      int i = name.indexOf(c, offset);
      if (i == -1) return name;
      if (i == 0 || name.charAt(i - 1) != '\\') {
        name = name.substring(0, i) + '\\' + name.substring(i);
      }
      offset = i + 2;
    }
  }

  public static String escapeValue(String value) {
    StringBuilder escapedName = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\n' && (i == 0 || value.charAt(i - 1) != '\\')
        || i == value.length()-1 && (c == ' ' || c == '\t')
        ) {
        escapedName.append('\\');
      }
      escapedName.append(c);
    }
    return escapedName.toString();
  }
}
