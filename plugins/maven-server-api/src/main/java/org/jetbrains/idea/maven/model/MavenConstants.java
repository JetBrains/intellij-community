// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.model;

import java.util.Arrays;
import java.util.List;

public final class MavenConstants {
  public static final String POM_EXTENSION = "pom";
  public static final String POM_XML = "pom.xml";

  public static final String[] POM_NAMES = new String[]{POM_XML, "pom.scala", "pom.groovy", "pom.atom", "pom.rb", "pom.yml", "pom.clj"};
  public static final String[] POM_EXTENSIONS = {"pom", "xml", "scala", "groovy", "atom", "rb", "yml", "clj"};

  public static final String SUPER_POM_4_0_XML = "pom-4.0.0.xml";
  public static final String SUPER_POM_4_1_XML = "pom-4.1.0.xml";
  public static final String PROFILES_XML = "profiles.xml";
  public static final String SETTINGS_XML = "settings.xml";

  public static final String PROFILE_FROM_POM = "pom";
  public static final String PROFILE_FROM_PROFILES_XML = "profiles.xml";
  public static final String PROFILE_FROM_SETTINGS_XML = "settings.xml";

  public static final String TYPE_POM = "pom";
  public static final String TYPE_JAR = "jar";
  public static final String TYPE_TEST_JAR = "test-jar";
  public static final String TYPE_WAR = "war";
  public static final String TYPE_EJB_CLIENT = "ejb-client";

  public static final String SCOPE_COMPILE = "compile";
  public static final String SCOPE_PROVIDED = "provided";
  public static final String SCOPE_RUNTIME = "runtime";
  public static final String SCOPE_TEST = "test";
  public static final String SCOPE_SYSTEM = "system";
  public static final String SCOPE_IMPORT = "import";

  public static final String HOME_PROPERTY = "maven.home";

  public static final List<String> PHASES =
    Arrays.asList("clean", "validate", "generate-sources", "process-sources", "generate-resources",
                  "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources",
                  "generate-test-resources",
                  "process-test-resources", "test-compile", "process-test-classes", "test", "prepare-package", "package",
                  "pre-integration-test",
                  "integration-test",
                  "post-integration-test",
                  "verify", "install", "site", "deploy");
  public static final List<String> BASIC_PHASES =
    Arrays.asList("clean", "validate", "compile", "test", "package", "verify", "install", "deploy", "site");

  public static final String JVM_CONFIG_RELATIVE_PATH = ".mvn/jvm.config";
  public static final String MAVEN_CONFIG_RELATIVE_PATH = ".mvn/maven.config";
  public static final String MAVEN_WRAPPER_RELATIVE_PATH = ".mvn/wrapper/maven-wrapper.properties";
}
