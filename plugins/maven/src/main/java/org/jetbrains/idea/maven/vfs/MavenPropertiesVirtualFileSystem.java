// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.vfs;

import com.intellij.lang.properties.IProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

import java.util.Map;
import java.util.Properties;

public class MavenPropertiesVirtualFileSystem extends DummyFileSystem {
  @NonNls public static final String PROTOCOL = "maven-properties";

  @NonNls public static final String SYSTEM_PROPERTIES_FILE = "System.properties";
  @NonNls public static final String ENV_PROPERTIES_FILE = "Environment.properties";

  public static final String[] PROPERTIES_FILES = new String[]{SYSTEM_PROPERTIES_FILE, ENV_PROPERTIES_FILE};

  private VirtualFile mySystemPropertiesFile;
  private VirtualFile myEnvPropertiesFile;

  public static MavenPropertiesVirtualFileSystem getInstance() {
    return (MavenPropertiesVirtualFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @Override
  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  public VirtualFile getSystemPropertiesFile() {
    if (mySystemPropertiesFile == null) {
      Properties systemProperties = new Properties();

      for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
        if (entry.getKey() instanceof String key && entry.getValue() instanceof String) {
          if (!key.startsWith("idea.")) {
            systemProperties.setProperty(key, (String)entry.getValue());
          }
        }
      }

      mySystemPropertiesFile = new MavenPropertiesVirtualFile(SYSTEM_PROPERTIES_FILE, systemProperties, this);
    }

    return mySystemPropertiesFile;
  }

  public VirtualFile getEnvPropertiesFile() {
    if (myEnvPropertiesFile == null) {
      Properties envProperties = new Properties();

      for (Map.Entry<String, String> each : System.getenv().entrySet()) {
        String key = each.getKey();
        if (key.startsWith("=")) continue;
        envProperties.setProperty(SystemInfo.isWindows ? StringUtil.toUpperCase(key) : key, each.getValue());
      }

      myEnvPropertiesFile = new MavenPropertiesVirtualFile(ENV_PROPERTIES_FILE, envProperties, this);
    }

    return myEnvPropertiesFile;
  }

  //@Override
  //public boolean isPhysical() {
  //  return false;
  //}

  @Override
  public synchronized VirtualFile findFileByPath(@NotNull @NonNls String path) {
    if (path.equals(SYSTEM_PROPERTIES_FILE)) {
      return getSystemPropertiesFile();
    }

    if (path.equals(ENV_PROPERTIES_FILE)) {
      return getEnvPropertiesFile();
    }

    return null;
  }

  @Nullable
  public IProperty findSystemProperty(Project project, @NotNull String propertyName) {
    return MavenDomUtil.findProperty(project, getSystemPropertiesFile(), propertyName);
  }

  @Nullable
  public IProperty findEnvProperty(Project project, @NotNull String propertyName) {
    return MavenDomUtil.findProperty(project, getEnvPropertiesFile(), propertyName);
  }

  //protected void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile copyFile(Object requestor, VirtualFile virtualFile, VirtualFile newParent, String copyName)
  //  throws IOException {
  //  throw new UnsupportedOperationException();
  //}
}
