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

import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.util.SystemInfo;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Ralf Quebbemann
 */
class MavenExternalParameters {

  public static class MavenConfigErrorException extends Exception {

    public MavenConfigErrorException(String string) {
      super(string);
    }

    public MavenConfigErrorException(Throwable throwable) {
      super(throwable);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static List<String> createCommand(MavenExecutor.Parameters buildParameters,
                                           MavenBuilderState builderState,
                                           MavenCoreState mavenCoreState) throws MavenConfigErrorException {
    @NonNls List<String> cmdList = new ArrayList<String>();

    cmdList.add(getJavaExecutable(builderState.getJdkPath()));

    addParameters(cmdList, builderState.getVmOptions());

    addParameters(cmdList, StringUtil.notNullize(System.getenv("MAVEN_OPTS")));

    final String mavenHome = resolveMavenHome(mavenCoreState.getMavenHome());

    addOption(cmdList, "classpath", getMavenClasspathEntries(mavenHome));

    addProperty(cmdList, "classworlds.conf", MavenEnv.getMavenConfFile(new File(mavenHome)).getPath());
    addProperty(cmdList, "maven.home", mavenHome);

    cmdList.add("org.codehaus.classworlds.Launcher");

    encodeCoreSettings(mavenCoreState, cmdList);

    if (builderState.isSkipTests()) {
      addProperty(cmdList, "test", "skip");
    }

    for (Map.Entry<String, String> entry : builderState.getMavenProperties().entrySet()) {
      addProperty(cmdList, entry.getKey(), entry.getValue());
    }

    addOption(cmdList, "f", buildParameters.getPomFile());

    for (String goal : buildParameters.getGoals()) {
      cmdList.add(goal);
    }

    return cmdList;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getJavaExecutable(String jdkName) {
    for (ProjectJdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(jdkName)) {
        return projectJdk.getVMExecutablePath();
      }
    }

    String javaHome = System.getenv("JAVA_HOME");
    if (!StringUtil.isEmptyOrSpaces(javaHome)) {
      return new File(new File(javaHome, "bin"), SystemInfo.isWindows ? "java.exe" : "java").getPath();
    }
    else {
      return ProjectJdkTable.getInstance().getInternalJdk().getVMExecutablePath();
    }
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

  private static String resolveMavenHome(@NotNull String mavenHome) throws MavenConfigErrorException {
    final File file = MavenEnv.resolveMavenHomeDirectory(mavenHome);

    if (file == null) {
      throw new MavenConfigErrorException(BuilderBundle.message("external.maven.home.no.default"));
    }

    if (!file.exists()) {
      throw new MavenConfigErrorException(BuilderBundle.message("external.maven.home.does.not.exist", mavenHome));
    }

    if (!MavenEnv.isValidMavenHome(file)) {
      throw new MavenConfigErrorException(BuilderBundle.message("external.maven.home.invalid", file.getPath()));
    }

    try {
      return file.getCanonicalPath();
    }
    catch (IOException e) {
      throw new MavenConfigErrorException(e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getMavenClasspathEntries(String mavenHome) {
    File mavenHomeBootAsFile = new File(new File(mavenHome, "core"), "boot");
    // if the dir "core/boot" does not exist we are using a Maven version > 2.0.5
    // in this case the classpath must be constructed from the dir "boot"
    if (!mavenHomeBootAsFile.exists()) {
      mavenHomeBootAsFile = new File(mavenHome, "boot");
    }
    StringBuffer classpathEntries = new StringBuffer();
    if (mavenHomeBootAsFile.exists()) {
      if (mavenHomeBootAsFile.isDirectory()) {
        for (File file : mavenHomeBootAsFile.listFiles()) {
          if (file.getName().startsWith("classworlds-")) {
            if (classpathEntries.length() != 0) {
              classpathEntries.append(File.pathSeparatorChar);
            }
            classpathEntries.append(file.getAbsolutePath());
          }
        }
      }
    }
    return classpathEntries.toString();
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
}
