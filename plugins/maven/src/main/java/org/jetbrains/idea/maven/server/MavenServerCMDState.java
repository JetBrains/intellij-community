// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.diagnostic.VMOptions;
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.issue.BuildIssueException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension;
import org.jetbrains.idea.maven.MavenVersionSupportUtil;
import org.jetbrains.idea.maven.buildtool.quickfix.InstallMaven2BuildIssue;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.slf4j.Logger;
import org.slf4j.jul.JDK14LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenServerCMDState extends CommandLineState {
  private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic
    .Logger.getInstance(MavenServerCMDState.class);
  private static boolean setupThrowMainClass = false;

  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.server.RemoteMavenServer";
  @NonNls private static final String MAIN_CLASS36 = "org.jetbrains.idea.maven.server.RemoteMavenServer36";
  @NonNls private static final String MAIN_CLASS40 = "com.intellij.maven.server.m40.RemoteMavenServer40";
  @NonNls private static final String MAIN_CLASS_WITH_EXCEPTION_FOR_TESTS =
    "org.jetbrains.idea.maven.server.RemoteMavenServerThrowsExceptionForTests";


  protected final Sdk myJdk;
  protected final String myVmOptions;
  protected final MavenDistribution myDistribution;
  protected final Integer myDebugPort;

  public MavenServerCMDState(@NotNull Sdk jdk,
                             @Nullable String vmOptions,
                             @NotNull MavenDistribution mavenDistribution,
                             @Nullable Integer debugPort) {
    super(null);
    myJdk = jdk;
    myVmOptions = vmOptions;
    myDistribution = mavenDistribution;
    myDebugPort = debugPort;
  }

  // Profile the Maven server if the idea is launched under profiling
  private static String getProfilerVMString() {
    String profilerOptionPrefix = "-agentpath:";
    String profilerVMOption = VMOptions.readOption(profilerOptionPrefix, true);
    // Doesn't work for macOS with java 11. Pending update to https://github.com/async-profiler/async-profiler/releases/tag/v3.0
    if (profilerVMOption == null || SystemInfo.isMac) return null;
    String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("mm:ss"));
    return profilerOptionPrefix + profilerVMOption
      .replace(".jfr", "-" + currentTime + "-maven.jfr")
      .replace(".log", "-" + currentTime + "-maven.log");
  }

  protected SimpleJavaParameters createJavaParameters() {
    final SimpleJavaParameters params = new SimpleJavaParameters();

    params.setJdk(myJdk);

    params.setWorkingDirectory(getWorkingDirectory());


    Map<String, String> defs = new HashMap<>(getMavenOpts());

    configureSslRelatedOptions(defs);

    defs.put("java.awt.headless", "true");
    for (Map.Entry<String, String> each : defs.entrySet()) {
      params.getVMParametersList().defineProperty(each.getKey(), each.getValue());
    }

    params.getVMParametersList().addProperty("maven.defaultProjectBuilder.disableGlobalModelCache", "true");

    if (myDebugPort != null) {
      params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:" + myDebugPort);
      params.getProgramParametersList().add("runWithDebugger");
    }

    params.getVMParametersList().addProperty("maven.defaultProjectBuilder.disableGlobalModelCache", "true");
    if (Registry.is("maven.collect.local.stat")) {
      params.getVMParametersList().addProperty("maven.collect.local.stat", "true");
    }

    String profilerOption = getProfilerVMString();
    if (profilerOption != null) {
      params.getVMParametersList()
        .addParametersString(profilerOption);
    }

    String xmxProperty = null;
    String xmsProperty = null;

    if (myVmOptions != null) {
      ParametersList mavenOptsList = new ParametersList();
      mavenOptsList.addParametersString(myVmOptions);

      for (String param : mavenOptsList.getParameters()) {
        if (param.startsWith("-Xmx")) {
          xmxProperty = param;
          continue;
        }
        if (param.startsWith("-Xms")) {
          xmsProperty = param;
          continue;
        }
        if (Registry.is("maven.server.vm.remove.javaagent") && param.startsWith("-javaagent")) {
          continue;
        }
        params.getVMParametersList().add(param);
      }
    }
    params.getVMParametersList().add("-Didea.version=" + MavenUtil.getIdeaVersionToPassToMavenProcess());

    setupMainClass(params, myDistribution.getVersion());

    params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION, myDistribution.getVersion());

    params.getClassPath().addAllFiles(collectClassPathAndLibsFolder(myDistribution));

    Collection<String> classPath = collectRTLibraries(myDistribution.getVersion());
    for (String s : classPath) {
      params.getClassPath().add(s);
    }

    String embedderXmx = System.getProperty("idea.maven.embedder.xmx");
    if (embedderXmx != null) {
      xmxProperty = "-Xmx" + embedderXmx;
    }
    else {
      if (xmxProperty == null) {
        xmxProperty = getMaxXmxStringValue("-Xmx768m", xmsProperty);
      }
    }
    params.getVMParametersList().add(xmsProperty);
    params.getVMParametersList().add(xmxProperty);


    String mavenEmbedderParameters = System.getProperty("idea.maven.embedder.parameters");
    if (mavenEmbedderParameters != null) {
      params.getProgramParametersList().addParametersString(mavenEmbedderParameters);
    }

    String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
    if (mavenEmbedderCliOptions != null) {
      params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS, mavenEmbedderCliOptions);
    }

    setupMainExt(params);
    return params;
  }

  public static @NotNull List<File> collectClassPathAndLibsFolder(@NotNull MavenDistribution distribution) {
    if (!distribution.isValid()) {
      MavenLog.LOG.warn("Maven Distribution " + distribution + " is not valid");
      throw new IllegalArgumentException("Maven distribution at" + distribution.getMavenHome().toAbsolutePath() + " is not valid");
    }

    MavenVersionAwareSupportExtension extension = MavenVersionSupportUtil.getExtensionFor(distribution);


    if (extension == null) {
      if (StringUtil.compareVersionNumbers(distribution.getVersion(), "3") < 0) {
        throw new BuildIssueException(new InstallMaven2BuildIssue());
      }
      throw new IllegalStateException("Maven distribution at" + distribution.getMavenHome().toAbsolutePath() + " is not supported");
    }
    MavenLog.LOG.info("Using extension " + extension + " to start MavenServer");
    return extension.collectClassPathAndLibsFolder(distribution);
  }

  private void setupMainExt(SimpleJavaParameters params) {
    //it is critical to setup maven.ext.class.path for maven >=3.6, otherwise project extensions will not be loaded
    MavenUtil.addEventListener(myDistribution.getVersion(), params);
  }

  private static void configureSslRelatedOptions(Map<String, String> defs) {
    for (Map.Entry<Object, Object> each : System.getProperties().entrySet()) {
      Object key = each.getKey();
      Object value = each.getValue();
      if (key instanceof String && value instanceof String && ((String)key).startsWith("javax.net.ssl")) {
        defs.put((String)key, (String)value);
      }
    }
  }

  protected Map<String, String> getMavenOpts() {
    return MavenUtil.getPropertiesFromMavenOpts();
  }

  @NotNull
  protected String getWorkingDirectory() {
    return PathManager.getBinPath();
  }

  protected @NotNull Collection<String> collectRTLibraries(String mavenVersion) {
    Set<String> classPath = new LinkedHashSet<>();
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
      classPath.add(PathUtil.getJarPathForClass(Logger.class));
      classPath.add(PathUtil.getJarPathForClass(JDK14LoggerFactory.class));
    }

    classPath.add(PathUtil.getJarPathForClass(StringUtilRt.class));//util-rt
    classPath.add(PathUtil.getJarPathForClass(NotNull.class));//annotations-java5
    classPath.add(PathUtil.getJarPathForClass(Element.class));//JDOM
    return classPath;
  }

  private static void setupMainClass(SimpleJavaParameters params, String mavenVersion) {
    if (setupThrowMainClass && MavenUtil.isMavenUnitTestModeEnabled()) {
      setupThrowMainClass = false;
      params.setMainClass(MAIN_CLASS_WITH_EXCEPTION_FOR_TESTS);
    }
    else if (StringUtil.compareVersionNumbers(mavenVersion, "4.0") >= 0) {
      params.setMainClass(MAIN_CLASS40);
    }
    else if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
      params.setMainClass(MAIN_CLASS36);
    }
    else {
      params.setMainClass(MAIN_CLASS);
    }
  }

  @NotNull
  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    ProcessHandler processHandler = startProcess();
    return new DefaultExecutionResult(processHandler);
  }

  @Override
  @NotNull
  protected ProcessHandler startProcess() throws ExecutionException {
    SimpleJavaParameters params = createJavaParameters();
    GeneralCommandLine commandLine = params.toCommandLine();
    OSProcessHandler processHandler = new OSProcessHandler.Silent(commandLine);
    processHandler.setShouldDestroyProcessRecursively(false);
    return processHandler;
  }

  @TestOnly
  public static void setThrowExceptionOnNextServerStart() {
    setupThrowMainClass = true;
  }

  @TestOnly
  public static void resetThrowExceptionOnNextServerStart() {
    setupThrowMainClass = false;
  }

  @Nullable
  static String getMaxXmxStringValue(@Nullable String memoryValueA, @Nullable String memoryValueB) {
    MemoryProperty propertyA = MemoryProperty.valueOf(memoryValueA);
    MemoryProperty propertyB = MemoryProperty.valueOf(memoryValueB);
    if (propertyA != null && propertyB != null) {
      MemoryProperty maxMemoryProperty = propertyA.valueBytes > propertyB.valueBytes ? propertyA : propertyB;
      return MemoryProperty.of(MemoryProperty.MemoryPropertyType.XMX, maxMemoryProperty.valueBytes).toString(maxMemoryProperty.unit);
    }
    return Optional
      .ofNullable(propertyA).or(() -> Optional.ofNullable(propertyB))
      .map(property -> MemoryProperty.of(MemoryProperty.MemoryPropertyType.XMX, property.valueBytes).toString(property.unit))
      .orElse(null);
  }

  private static class MemoryProperty {
    private static final Pattern MEMORY_PROPERTY_PATTERN = Pattern.compile("^(-Xmx|-Xms)(\\d+)([kK]|[mM]|[gG])?$");
    final String type;
    final long valueBytes;
    final MemoryUnit unit;

    private MemoryProperty(@NotNull String type, long value, @Nullable String unit) {
      this.type = type;
      this.unit = unit != null ? MemoryUnit.valueOf(unit.toUpperCase()) : MemoryUnit.B;
      this.valueBytes = value * this.unit.ratio;
    }

    @NotNull
    public static MemoryProperty of(@NotNull MemoryPropertyType propertyType, long bytes) {
      return new MemoryProperty(propertyType.type, bytes, MemoryUnit.B.name());
    }

    @Nullable
    public static MemoryProperty valueOf(@Nullable String value) {
      if (value == null) return null;
      Matcher matcher = MEMORY_PROPERTY_PATTERN.matcher(value);
      if (matcher.find()) {
        return new MemoryProperty(matcher.group(1), Long.parseLong(matcher.group(2)), matcher.group(3));
      }
      LOG.warn(value + " not match " + MEMORY_PROPERTY_PATTERN);
      return null;
    }

    @Override
    public String toString() {
      return toString(unit);
    }

    public String toString(MemoryUnit unit) {
      return type + valueBytes / unit.ratio + unit.name().toLowerCase();
    }

    private enum MemoryUnit {
      B(1), K(B.ratio * 1024), M(K.ratio * 1024), G(M.ratio * 1024);
      final int ratio;

      MemoryUnit(int ratio) {
        this.ratio = ratio;
      }
    }

    private enum MemoryPropertyType {
      XMX("-Xmx"), XMS("-Xms");
      private final String type;

      MemoryPropertyType(String type) {
        this.type = type;
      }
    }
  }
}
