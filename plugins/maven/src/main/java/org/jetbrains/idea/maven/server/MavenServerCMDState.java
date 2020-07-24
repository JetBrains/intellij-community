// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.apache.lucene.search.Query;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.slf4j.Logger;
import org.slf4j.impl.Log4jLoggerFactory;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenServerCMDState extends CommandLineState {

  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.server.RemoteMavenServer";
  @NonNls private static final String MAIN_CLASS36 = "org.jetbrains.idea.maven.server.RemoteMavenServer36";


  private final Sdk myJdk;
  private final String myVmOptions;
  private final MavenDistribution myDistribution;
  private final Project myProject;
  private final Integer myDebugPort;

  public MavenServerCMDState(@NotNull Sdk jdk,
                             @Nullable String vmOptions,
                             @Nullable MavenDistribution mavenDistribution,
                             Project project,
                             @Nullable Integer debugPort) {
    super(null);
    myJdk = jdk;
    myVmOptions = vmOptions;
    myDistribution = mavenDistribution;
    myProject = project;
    myDebugPort = debugPort;
  }

  SimpleJavaParameters createJavaParameters() {
    final SimpleJavaParameters params = new SimpleJavaParameters();

    params.setJdk(myJdk);

    params.setWorkingDirectory(PathManager.getBinPath());


    Map<String, String> defs = new THashMap<>();
    defs.putAll(MavenUtil.getPropertiesFromMavenOpts());

    // pass ssl-related options
    for (Map.Entry<Object, Object> each : System.getProperties().entrySet()) {
      Object key = each.getKey();
      Object value = each.getValue();
      if (key instanceof String && value instanceof String && ((String)key).startsWith("javax.net.ssl")) {
        defs.put((String)key, (String)value);
      }
    }

    defs.put("java.awt.headless", "true");
    for (Map.Entry<String, String> each : defs.entrySet()) {
      params.getVMParametersList().defineProperty(each.getKey(), each.getValue());
    }

    params.getVMParametersList().addProperty("maven.defaultProjectBuilder.disableGlobalModelCache", "true");

    if (myDebugPort != null) {
      params.getVMParametersList()
        .addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=" + myDebugPort);
    }

    params.getVMParametersList().addProperty("maven.defaultProjectBuilder.disableGlobalModelCache", "true");

    boolean xmxSet = false;

    if (myVmOptions != null) {
      ParametersList mavenOptsList = new ParametersList();
      mavenOptsList.addParametersString(myVmOptions);

      for (String param : mavenOptsList.getParameters()) {
        if (param.startsWith("-Xmx")) {
          xmxSet = true;
        }
        params.getVMParametersList().add(param);
      }
    }

    final File mavenHome;
    final String mavenVersion;
    final MavenDistribution distribution = myDistribution;

    if (distribution == null) {
      MavenLog.LOG.warn("Not found maven at ");
      MavenDistribution embedded = MavenServerManager.resolveEmbeddedMavenHome();
      mavenHome = embedded.getMavenHome();
      mavenVersion = embedded.getVersion();
      showInvalidMavenNotification(mavenVersion);
    }
    else {
      mavenHome = distribution.getMavenHome();
      mavenVersion = distribution.getVersion();
    }
    MavenLog.LOG.debug("", distribution, " chosen as maven home");
    assert mavenVersion != null;

    if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
      params.setMainClass(MAIN_CLASS36);
    }
    else {
      params.setMainClass(MAIN_CLASS);
    }

    params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION, mavenVersion);

    final List<String> classPath = new ArrayList<>();
    classPath.add(PathUtil.getJarPathForClass(org.apache.log4j.Logger.class));
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
      classPath.add(PathUtil.getJarPathForClass(Logger.class));
      classPath.add(PathUtil.getJarPathForClass(Log4jLoggerFactory.class));
    }

    classPath.add(PathUtil.getJarPathForClass(StringUtilRt.class));//util-rt
    classPath.add(PathUtil.getJarPathForClass(NotNull.class));//annotations-java5
    classPath.add(PathUtil.getJarPathForClass(Element.class));//JDOM
    classPath.add(PathUtil.getJarPathForClass(TIntHashSet.class));//Trove

    ContainerUtil.addIfNotNull(classPath, PathUtil.getJarPathForClass(Query.class));
    params.getClassPath().add(PathManager.getResourceRoot(getClass(), "/messages/CommonBundle.properties"));
    params.getClassPath().addAll(classPath);
    params.getClassPath().addAllFiles(MavenServerManager.collectClassPathAndLibsFolder(mavenVersion, mavenHome));

    String embedderXmx = System.getProperty("idea.maven.embedder.xmx");
    if (embedderXmx != null) {
      params.getVMParametersList().add("-Xmx" + embedderXmx);
    }
    else {
      if (!xmxSet) {
        params.getVMParametersList().add("-Xmx768m");
      }
    }


    String mavenEmbedderParameters = System.getProperty("idea.maven.embedder.parameters");
    if (mavenEmbedderParameters != null) {
      params.getProgramParametersList().addParametersString(mavenEmbedderParameters);
    }

    String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
    if (mavenEmbedderCliOptions != null) {
      params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS, mavenEmbedderCliOptions);
    }

    MavenUtil.addEventListener(mavenVersion, params);
    return params;
  }

  @NotNull
  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    ProcessHandler processHandler = startProcess();
    return new DefaultExecutionResult(processHandler);
  }

  @Override
  @NotNull
  protected OSProcessHandler startProcess() throws ExecutionException {
    SimpleJavaParameters params = createJavaParameters();
    GeneralCommandLine commandLine = params.toCommandLine();
    OSProcessHandler processHandler = new OSProcessHandler.Silent(commandLine);
    processHandler.setShouldDestroyProcessRecursively(false);
    return processHandler;
  }

  private void showInvalidMavenNotification(@Nullable String mavenVersion) {
    String message = invalidHomeMessageToShow(myDistribution, mavenVersion, myProject);

    NotificationListener listener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, MavenProjectBundle.message("configurable.MavenSettings.display.name"));
      }
    };

    new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "", message, NotificationType.WARNING, listener).notify(myProject);
  }

  private static String invalidHomeMessageToShow(@Nullable MavenDistribution mavenDistribution,
                                                 String substitutedVersion,
                                                 Project project) {
    if (mavenDistribution != null && StringUtil.equals(MavenServerManager.BUNDLED_MAVEN_2, mavenDistribution.getName())) {
      if (project == null) {
        return RunnerBundle.message("bundled.maven.maven2.not.supported");
      }
      else {
        return RunnerBundle.message("bundled.maven.maven2.not.supported.with.fix");
      }
    }
    else {
      String wrongDir = mavenDistribution == null ? null : mavenDistribution.getMavenHome().getAbsolutePath();
      if (project == null) {
        return RunnerBundle
          .message("external.maven.home.invalid.substitution.warning", wrongDir, substitutedVersion);
      }
      else {
        return RunnerBundle.message("external.maven.home.invalid.substitution.warning.with.fix", wrongDir, substitutedVersion);
      }
    }
  }
}
