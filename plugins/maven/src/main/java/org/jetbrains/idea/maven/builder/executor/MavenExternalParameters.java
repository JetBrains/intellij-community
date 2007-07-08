/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */


package org.jetbrains.idea.maven.builder.executor;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.builder.BuilderBundle;
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.core.MavenCoreState;
import org.jetbrains.idea.maven.core.util.MavenEnv;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author Ralf Quebbemann
 */
public class MavenExternalParameters {
  public static final String MAVEN_LAUNCHER_CLASS = "org.codehaus.classworlds.Launcher";
  @NonNls private static final String JAVA_HOME = "JAVA_HOME";
  @NonNls private static final String MAVEN_OPTS = "MAVEN_OPTS";

  public static JavaParameters createJavaParameters(final MavenBuildParameters parameters,
                                                    final MavenCoreState coreState,
                                                    final MavenBuilderState builderState) throws ExecutionException {
    final JavaParameters params = new JavaParameters();

    params.setWorkingDirectory(parameters.getWorkingDir());

    params.setJdk(getJdk(builderState.getJreName()));

    final String mavenHome = resolveMavenHome(coreState);

    for (String parameter : createVMParameters(new ArrayList<String>(), mavenHome, builderState)) {
      params.getVMParametersList().add(parameter);
    }

    for (String path : getMavenClasspathEntries(mavenHome)) {
      params.getClassPath().add(path);
    }

    params.setMainClass(MAVEN_LAUNCHER_CLASS);

    for (String parameter : createMavenParameters(new ArrayList<String>(), coreState, builderState, parameters)) {
      params.getProgramParametersList().add(parameter);
    }

    return params;
  }

  @NotNull
  private static ProjectJdk getJdk(final String name) throws ExecutionException {
    if (name.equals(MavenBuilderState.USE_INTERNAL_JAVA)) {
      return ProjectJdkTable.getInstance().getInternalJdk();
    }

    if (name.equals(MavenBuilderState.USE_JAVA_HOME)) {
      final String javaHome = System.getenv(JAVA_HOME);
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw new ExecutionException(MessageFormat.format(BuilderBundle.message("maven.java.home.undefined"), name));
      }
      final ProjectJdk jdk = JavaSdk.getInstance().createJdk("", javaHome);
      if (jdk == null) {
        throw new ExecutionException(MessageFormat.format(BuilderBundle.message("maven.java.home.invalid"), name));
      }
      return jdk;
    }

    for (ProjectJdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(name)) {
        return projectJdk;
      }
    }

    throw new ExecutionException(MessageFormat.format(BuilderBundle.message("maven.java.not.found"), name));
  }

  public static List<String> createVMParameters(final List<String> list, final String mavenHome, final MavenBuilderState builderState) {
    addParameters(list, builderState.getVmOptions());

    addParameters(list, StringUtil.notNullize(System.getenv(MAVEN_OPTS)));

    addProperty(list, "classworlds.conf", MavenEnv.getMavenConfFile(new File(mavenHome)).getPath());

    addProperty(list, "maven.home", mavenHome);

    return list;
  }

  private static List<String> createMavenParameters(final List<String> list,
                                                    final MavenCoreState coreState,
                                                    final MavenBuilderState builderState,
                                                    final MavenBuildParameters parameters) {
    encodeCoreSettings(coreState, list);

    if (builderState.isSkipTests()) {
      addProperty(list, "test", "skip");
    }

    for (Map.Entry<String, String> entry : builderState.getMavenProperties().entrySet()) {
      addProperty(list, entry.getKey(), entry.getValue());
    }

    addOption(list, "f", parameters.getPomPath());

    for (String goal : parameters.getGoals()) {
      list.add(goal);
    }

    addOption(list, "P", encodeProfiles(parameters.getProfiles()));

    return list;
  }

  private static void addOption(List<String> cmdList, @NonNls String key, @NonNls String value) {
    if (!StringUtil.isEmptyOrSpaces(value)) {
      cmdList.add("-" + key);
      cmdList.add(value);
    }
  }

  private static void addParameters(List<String> cmdList, String parameters) {
    if (!StringUtil.isEmptyOrSpaces(parameters)) {
      StringTokenizer tokenizer = new StringTokenizer(parameters);
      while (tokenizer.hasMoreTokens()) {
        cmdList.add(tokenizer.nextToken());
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void addProperty(List<String> cmdList, @NonNls String key, @NonNls String value) {
    cmdList.add(MessageFormat.format("-D{0}={1}", key, value));
  }

  private static String resolveMavenHome(@NotNull MavenCoreState coreState) throws ExecutionException {
    final File file = MavenEnv.resolveMavenHomeDirectory(coreState.getMavenHome());

    if (file == null) {
      throw new ExecutionException(BuilderBundle.message("external.maven.home.no.default"));
    }

    if (!file.exists()) {
      throw new ExecutionException(BuilderBundle.message("external.maven.home.does.not.exist", file.getPath()));
    }

    if (!MavenEnv.isValidMavenHome(file)) {
      throw new ExecutionException(BuilderBundle.message("external.maven.home.invalid", file.getPath()));
    }

    try {
      return file.getCanonicalPath();
    }
    catch (IOException e) {
      throw new ExecutionException(e.getMessage(), e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static List<String> getMavenClasspathEntries(final String mavenHome) {
    File mavenHomeBootAsFile = new File(new File(mavenHome, "core"), "boot");
    // if the dir "core/boot" does not exist we are using a Maven version > 2.0.5
    // in this case the classpath must be constructed from the dir "boot"
    if (!mavenHomeBootAsFile.exists()) {
      mavenHomeBootAsFile = new File(mavenHome, "boot");
    }
    List<String> classpathEntries = new ArrayList<String>();
    if (mavenHomeBootAsFile.exists()) {
      if (mavenHomeBootAsFile.isDirectory()) {
        for (File file : mavenHomeBootAsFile.listFiles()) {
          if (file.getName().startsWith("classworlds-")) {
            classpathEntries.add(file.getAbsolutePath());
          }
        }
      }
    }
    return classpathEntries;
  }

  private static void encodeCoreSettings(MavenCoreState mavenCoreState, @NonNls List<String> cmdList) {
    if (mavenCoreState.isWorkOffline()) {
      cmdList.add("--offline");
    }
    if (!mavenCoreState.isUsePluginRegistry()) {
      cmdList.add("--no-plugin-registry");
    }
    if (mavenCoreState.getOutputLevel() == MavenExecutionRequest.LOGGING_LEVEL_DEBUG) {
      cmdList.add("--debug");
    }

    if (mavenCoreState.isNonRecursive()) {
      cmdList.add("--non-recursive");
    }

    if (mavenCoreState.isProduceExceptionErrorMessages()) {
      cmdList.add("--errors");
    }

    cmdList.add("--" + mavenCoreState.getFailureBehavior());

    if (mavenCoreState.getPluginUpdatePolicy()) {
      cmdList.add("--check-plugin-updates");
    }
    else {
      cmdList.add("--no-plugin-updates");
    }

    if (mavenCoreState.getChecksumPolicy().length() != 0) {
      if (mavenCoreState.getChecksumPolicy().equals(MavenExecutionRequest.CHECKSUM_POLICY_FAIL)) {
        cmdList.add("--strict-checksums");
      }
      else {
        cmdList.add("--lax-checksums");
      }
    }

    addOption(cmdList, "s", mavenCoreState.getMavenSettingsFile());
  }

  private static String encodeProfiles(final Collection<String> profiles) {
    final StringBuilder stringBuilder = new StringBuilder();
    for (String profile : profiles) {
      if(stringBuilder.length()!=0){
        stringBuilder.append(",");
      }
      stringBuilder.append(profile);
    }
    return stringBuilder.toString();
  }
}
