/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class MavenSchemaProvider implements StandardResourceProvider {
  public static final String MAVEN_PROJECT_SCHEMA_URL = "http://maven.apache.org/xsd/maven-4.0.0.xsd";
  public static final String MAVEN_PROFILES_SCHEMA_URL = "http://maven.apache.org/xsd/profiles-1.0.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL = "http://maven.apache.org/xsd/settings-1.0.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL_1_1 = "http://maven.apache.org/xsd/settings-1.1.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL_1_2 = "http://maven.apache.org/xsd/settings-1.2.0.xsd";

  public static final List<String>  MAVEN_SETTINGS_SCHEMAS = List.of(MAVEN_SETTINGS_SCHEMA_URL,
                                                                     MAVEN_SETTINGS_SCHEMA_URL_1_1,
                                                                     MAVEN_SETTINGS_SCHEMA_URL_1_2);

  @Override
  public void registerResources(ResourceRegistrar registrar) {
    Map.of(
      MAVEN_PROJECT_SCHEMA_URL, "/schemas/maven-4.0.0.xsd",
      "http://maven.apache.org/maven-v4_0_0.xsd", "/schemas/maven-4.0.0.xsd",
      MAVEN_PROFILES_SCHEMA_URL, "/schemas/profiles-1.0.0.xsd",
      MAVEN_SETTINGS_SCHEMA_URL, "/schemas/settings-1.0.0.xsd",
      MAVEN_SETTINGS_SCHEMA_URL_1_1, "/schemas/settings-1.1.0.xsd",
      MAVEN_SETTINGS_SCHEMA_URL_1_2, "/schemas/settings-1.2.0.xsd"
    ).forEach((schemaUrl, schemaPath) -> addStdResource(registrar, schemaUrl, schemaPath));
  }

  private void addStdResource(ResourceRegistrar registrar, String schemaUrl, String schemaPath) {
    registrar.addStdResource(schemaUrl, schemaPath, getClass());
    if (schemaUrl.startsWith("http://")) {
      String schemaUrlHttps = schemaUrl.replace("http://", "https://");
      registrar.addStdResource(schemaUrlHttps, schemaPath, getClass());
    }
  }

  @NotNull
  public static VirtualFile getSchemaFile(@NotNull String url) {
    String location = ((ExternalResourceManagerEx)ExternalResourceManager.getInstance()).getStdResource(url, null);
    assert location != null : "cannot find a standard resource for " + url;

    VirtualFile result = VfsUtilCore.findRelativeFile(location, null);
    assert result != null : "cannot find a schema file for URL: " + url + " location: " + location;

    return result;
  }
}

