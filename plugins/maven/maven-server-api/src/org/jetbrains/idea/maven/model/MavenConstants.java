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
package org.jetbrains.idea.maven.model;

import java.util.Arrays;
import java.util.List;

public class MavenConstants {
  public static final String POM_EXTENSION = "pom";
  public static final String POM_XML = "pom.xml";
  public static final String SUPER_POM_XML = "pom-4.0.0.xml";
  public static final String PROFILES_XML = "profiles.xml";
  public static final String SETTINGS_XML = "settings.xml";

  public static final String PROFILE_FROM_POM = "pom";
  public static final String PROFILE_FROM_PROFILES_XML = "profiles.xml";
  public static final String PROFILE_FROM_SETTINGS_XML = "settings.xml";

  public static final String TYPE_POM = "pom";
  public static final String TYPE_JAR = "jar";
  public static final String TYPE_TEST_JAR = "test-jar";
  public static final String TYPE_WAR = "war";

  public static final String SCOPE_COMPILE = "compile";
  public static final String SCOPE_PROVIDEED = "provided";
  public static final String SCOPE_RUNTIME = "runtime";
  public static final String SCOPE_TEST = "test";
  public static final String SCOPE_SYSTEM = "system";
  public static final String SCOPE_IMPORT = "import";

  public static final List<String> PHASES =
    Arrays.asList("clean", "validate", "generate-sources", "process-sources", "generate-resources",
                  "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources",
                  "generate-test-resources",
                  "process-test-resources", "test-compile", "test", "prepare-package", "package", "pre-integration-test",
                  "integration-test",
                  "post-integration-test",
                  "verify", "install", "site", "deploy");
  public static final List<String> BASIC_PHASES =
    Arrays.asList("clean", "validate", "compile", "test", "package", "install", "deploy", "site");

}
