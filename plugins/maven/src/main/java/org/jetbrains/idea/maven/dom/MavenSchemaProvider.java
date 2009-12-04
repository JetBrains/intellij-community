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
package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenLog;

public class MavenSchemaProvider implements StandardResourceProvider {
  public static final String MAVEN_PROJECT_SCHEMA_URL = "http://maven.apache.org/xsd/maven-4.0.0.xsd";
  public static final String MAVEN_PROFILES_SCHEMA_URL = "http://maven.apache.org/xsd/profiles-1.0.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL = "http://maven.apache.org/xsd/settings-1.0.0.xsd";

  public void registerResources(ResourceRegistrar registrar) {
    registrar.addStdResource("http://maven.apache.org/maven-v4_0_0.xsd", "maven-4.0.0.xsd", getClass());
    registrar.addStdResource(MAVEN_PROJECT_SCHEMA_URL, "maven-4.0.0.xsd", getClass());

    registrar.addStdResource(MAVEN_PROFILES_SCHEMA_URL, "profiles-1.0.0.xsd", getClass());
    registrar.addStdResource(MAVEN_SETTINGS_SCHEMA_URL, "settings-1.0.0.xsd", getClass());
  }

  @NotNull
  public static VirtualFile getSchemaFile(@NotNull String url) {
    String location = ExternalResourceManager.getInstance().getResourceLocation(url);
    VirtualFile result = VfsUtil.findRelativeFile(location, null);
    if (result == null) {
      MavenLog.LOG.error("Cannot find a schema file for URL: " + url + " location: " + location);
    }
    return result;
  }
}

