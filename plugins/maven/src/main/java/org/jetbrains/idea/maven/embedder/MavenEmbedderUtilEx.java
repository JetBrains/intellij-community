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
package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.maven.embedder.MavenEmbedderUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public class MavenEmbedderUtilEx extends MavenEmbedderUtil {
  private static final String REPOSITORY_DIR = "repository";

  private static final String LIB_DIR = "lib";
  private static final String SUPER_POM_PATH = "org/apache/maven/project/" + MavenConstants.SUPER_POM_XML;

  @NotNull
  public static VirtualFile resolveSuperPomFile(@Nullable String overrideMavenHome) {
    VirtualFile result = doResolveSuperPomFile(overrideMavenHome);
    if (result == null) {
      URL resource = MavenEmbedderUtilEx.class.getResource("/" + SUPER_POM_PATH);
      return VfsUtil.findFileByURL(resource);
    }
    return result;
  }

  @Nullable
  private static VirtualFile doResolveSuperPomFile(String overrideMavenHome) {
    File lib = resolveMavenLib(overrideMavenHome);
    if (lib == null) return null;

    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(lib);
    if (file == null) return null;

    VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    if (root == null) return null;

    return root.findFileByRelativePath(SUPER_POM_PATH);
  }

  @Nullable
  private static File resolveMavenLib(String overrideMavenHome) {
    File directory = resolveMavenHomeDirectory(overrideMavenHome);
    if (directory == null) return null;
    File libs = new File(directory, LIB_DIR);
    File[] files = libs.listFiles();
    if (files != null) {
      Pattern pattern = Pattern.compile("maven-\\d+\\.\\d+\\.\\d+-uber\\.jar");
      for (File each : files) {
        if (pattern.matcher(each.getName()).matches()) {
          return each;
        }
      }
    }
    return null;
  }

  @NotNull
  public static File resolveLocalRepository(@Nullable String mavenHome, @Nullable String userSettings, @Nullable String override) {
    File result = doResolveLocalRepository(mavenHome, userSettings, override);
    try {
      return result.getCanonicalFile();
    }
    catch (IOException e) {
      return result;
    }
  }

  @NotNull
  private static File doResolveLocalRepository(String mavenHome, String userSettings, String override) {
    if (!StringUtil.isEmpty(override)) {
      return new File(override);
    }

    final File userSettingsFile = resolveUserSettingsFile(userSettings);
    if (userSettingsFile != null) {
      final String fromUserSettings = getRepositoryFromSettings(userSettingsFile);
      if (!StringUtil.isEmpty(fromUserSettings)) {
        return new File(fromUserSettings);
      }
    }

    final File globalSettingsFile = resolveGlobalSettingsFile(mavenHome);
    if (globalSettingsFile != null) {
      final String fromGlobalSettings = getRepositoryFromSettings(globalSettingsFile);
      if (!StringUtil.isEmpty(fromGlobalSettings)) {
        return new File(fromGlobalSettings);
      }
    }

    return new File(resolveM2Dir(), REPOSITORY_DIR);
  }

  @Nullable
  private static String getRepositoryFromSettings(final File file) {
    try {
      byte[] bytes = FileUtil.loadFileBytes(file);
      return expandProperties(MavenJDOMUtil.findChildValueByPath(MavenJDOMUtil.read(bytes, null), "localRepository", null));
    }
    catch (IOException e) {
      return null;
    }
  }

  private static String expandProperties(String text) {
    if (StringUtil.isEmptyOrSpaces(text)) return text;
    Properties props = MavenEmbedderWrapper.collectSystemProperties();
    for (Map.Entry<Object, Object> each : props.entrySet()) {
      text = text.replace("${" + each.getKey() + "}", (CharSequence)each.getValue());
    }
    return text;
  }
}