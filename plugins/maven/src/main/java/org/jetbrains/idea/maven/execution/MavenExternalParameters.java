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


package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

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

  public static void configureSimpleJavaParameters(final SimpleJavaParameters params,
                                                    final MavenRunnerParameters parameters,
                                                    final MavenGeneralSettings coreSettings,
                                                    final MavenRunnerSettings runnerSettings) throws ExecutionException {
    params.setJdk(getJdk(runnerSettings.getJreName()));
    for (String parameter : createVMParameters(new ArrayList<String>(), "", runnerSettings)) {
      params.getVMParametersList().add(parameter);
    }

    for (String parameter : createMavenParameters(new ArrayList<String>(), coreSettings, runnerSettings, parameters)) {
      if (!StringUtil.isEmpty(parameter)) {
        params.getProgramParametersList().add(parameter);
      }
    }
  }

  public static JavaParameters createJavaParameters(final MavenRunnerParameters parameters,
                                                    final MavenGeneralSettings coreSettings,
                                                    final MavenRunnerSettings runnerSettings) throws ExecutionException {
    final JavaParameters params = new JavaParameters();

    params.setWorkingDirectory(parameters.getWorkingDirFile());

    params.setJdk(getJdk(runnerSettings.getJreName()));

    final String mavenHome = resolveMavenHome(coreSettings);

    for (String parameter : createVMParameters(new ArrayList<String>(), mavenHome, runnerSettings)) {
      params.getVMParametersList().add(parameter);
    }

    for (String path : getMavenClasspathEntries(mavenHome)) {
      params.getClassPath().add(path);
    }

    params.setMainClass(MAVEN_LAUNCHER_CLASS);

    for (String parameter : createMavenParameters(new ArrayList<String>(), coreSettings, runnerSettings, parameters)) {
      if (!StringUtil.isEmpty(parameter)) {
        params.getProgramParametersList().add(parameter);
      }
    }

    return params;
  }

  @NotNull
  private static Sdk getJdk(final String name) throws ExecutionException {
    if (name.equals(MavenRunnerSettings.USE_INTERNAL_JAVA)) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }

    if (name.equals(MavenRunnerSettings.USE_JAVA_HOME)) {
      final String javaHome = System.getenv(JAVA_HOME);
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw new ExecutionException(RunnerBundle.message("maven.java.home.undefined"));
      }
      final Sdk jdk = JavaSdk.getInstance().createJdk("", javaHome);
      if (jdk == null) {
        throw new ExecutionException(RunnerBundle.message("maven.java.home.invalid", javaHome));
      }
      return jdk;
    }

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(name)) {
        return projectJdk;
      }
    }

    throw new ExecutionException(RunnerBundle.message("maven.java.not.found", name));
  }

  public static List<String> createVMParameters(final List<String> list, final String mavenHome, final MavenRunnerSettings runnerSettings) {
    addParameters(list, runnerSettings.getVmOptions());

    addParameters(list, StringUtil.notNullize(System.getenv(MAVEN_OPTS)));

    addProperty(list, "classworlds.conf", MavenUtil.getMavenConfFile(new File(mavenHome)).getPath());

    addProperty(list, "maven.home", mavenHome);

    return list;
  }

  private static List<String> createMavenParameters(final List<String> list,
                                                    final MavenGeneralSettings coreSettings,
                                                    final MavenRunnerSettings runnerSettings,
                                                    final MavenRunnerParameters parameters) {
    encodeCoreAndRunnerSettings(coreSettings, runnerSettings, list);

    if (runnerSettings.isSkipTests()) {
      addProperty(list, "skipTests", "true");
    }

    for (Map.Entry<String, String> entry : runnerSettings.getMavenProperties().entrySet()) {
      addProperty(list, entry.getKey(), entry.getValue());
    }


    if (parameters.getPomFilePath() != null) {
      addOption(list, "f", parameters.getPomFilePath());
    }

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

  private static String resolveMavenHome(@NotNull MavenGeneralSettings coreSettings) throws ExecutionException {
    final File file = MavenUtil.resolveMavenHomeDirectory(coreSettings.getMavenHome());

    if (file == null) {
      throw new ExecutionException(RunnerBundle.message("external.maven.home.no.default"));
    }

    if (!file.exists()) {
      throw new ExecutionException(RunnerBundle.message("external.maven.home.does.not.exist", file.getPath()));
    }

    if (!MavenUtil.isValidMavenHome(file)) {
      throw new ExecutionException(RunnerBundle.message("external.maven.home.invalid", file.getPath()));
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
          if (file.getName().contains("classworlds")) {
            classpathEntries.add(file.getAbsolutePath());
          }
        }
      }
    }
    return classpathEntries;
  }

  private static void encodeCoreAndRunnerSettings(MavenGeneralSettings coreSettings, MavenRunnerSettings runnerSettings,
                                                  @NonNls List<String> cmdList) {
    if (coreSettings.isWorkOffline()) {
      cmdList.add("--offline");
    }
    if (!coreSettings.isUsePluginRegistry()) {
      cmdList.add("--no-plugin-registry");
    }
    if (coreSettings.getLoggingLevel() == MavenExecutionOptions.LoggingLevel.DEBUG) {
      cmdList.add("--debug");
    }
    if (coreSettings.isNonRecursive()) {
      cmdList.add("--non-recursive");
    }
    if (coreSettings.isPrintErrorStackTraces()) {
      cmdList.add("--errors");
    }

    cmdList.add(coreSettings.getFailureBehavior().getCommandLineOption());
    cmdList.add(coreSettings.getPluginUpdatePolicy().getCommandLineOption());
    cmdList.add(coreSettings.getChecksumPolicy().getCommandLineOption());
    cmdList.add(coreSettings.getSnapshotUpdatePolicy().getCommandLineOption());

    addOption(cmdList, "s", coreSettings.getMavenSettingsFile());
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
