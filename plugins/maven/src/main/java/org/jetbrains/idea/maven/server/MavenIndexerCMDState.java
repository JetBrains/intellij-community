// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.apache.commons.lang.StringUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenIndexerCMDState extends CommandLineState {

  private static final String dependenciesOutput = """
      [INFO] +- org.apache.maven.indexer:indexer-core:jar:6.2.2:compile
      [INFO] |  +- org.slf4j:slf4j-api:jar:1.7.36:compile
      [INFO] |  +- javax.inject:javax.inject:jar:1:compile
      [INFO] |  +- org.apache.lucene:lucene-core:jar:8.11.1:compile
      [INFO] |  +- org.apache.lucene:lucene-queryparser:jar:8.11.1:compile
      [INFO] |  |  +- org.apache.lucene:lucene-queries:jar:8.11.1:compile
      [INFO] |  |  \\- org.apache.lucene:lucene-sandbox:jar:8.11.1:compile
      [INFO] |  +- org.apache.lucene:lucene-analyzers-common:jar:8.11.1:compile
      [INFO] |  +- org.apache.lucene:lucene-backward-codecs:jar:8.11.1:compile
      [INFO] |  +- org.apache.lucene:lucene-highlighter:jar:8.11.1:compile
      [INFO] |  |  \\- org.apache.lucene:lucene-memory:jar:8.11.1:compile
      [INFO] |  +- org.apache.maven.resolver:maven-resolver-api:jar:1.8.0:compile
      [INFO] |  +- org.apache.maven.resolver:maven-resolver-util:jar:1.8.0:compile
      [INFO] |  \\- org.apache.maven:maven-model:jar:3.8.5:compile
      [INFO] +- org.apache.maven:maven-core:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven:maven-settings:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven:maven-settings-builder:jar:3.8.3:compile
      [INFO] |  |  \\- org.codehaus.plexus:plexus-sec-dispatcher:jar:2.0:compile
      [INFO] |  |     \\- org.codehaus.plexus:plexus-cipher:jar:2.0:compile
      [INFO] |  +- org.apache.maven:maven-builder-support:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven:maven-repository-metadata:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven:maven-artifact:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven:maven-plugin-api:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven:maven-model-builder:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven:maven-resolver-provider:jar:3.8.3:compile
      [INFO] |  +- org.apache.maven.resolver:maven-resolver-impl:jar:1.6.3:compile
      [INFO] |  +- org.apache.maven.resolver:maven-resolver-spi:jar:1.6.3:compile
      [INFO] |  +- org.apache.maven.shared:maven-shared-utils:jar:3.3.4:compile
      [INFO] |  +- org.eclipse.sisu:org.eclipse.sisu.plexus:jar:0.3.5:compile
      [INFO] |  |  \\- javax.annotation:javax.annotation-api:jar:1.2:compile
      [INFO] |  +- org.eclipse.sisu:org.eclipse.sisu.inject:jar:0.3.5:compile
      [INFO] |  +- com.google.inject:guice:jar:no_aop:4.2.2:compile
      [INFO] |  |  +- aopalliance:aopalliance:jar:1.0:compile
      [INFO] |  |  \\- com.google.guava:guava:jar:25.1-android:compile
      [INFO] |  |     +- com.google.code.findbugs:jsr305:jar:3.0.2:compile
      [INFO] |  |     +- org.checkerframework:checker-compat-qual:jar:2.0.0:compile
      [INFO] |  |     +- com.google.errorprone:error_prone_annotations:jar:2.1.3:compile
      [INFO] |  |     +- com.google.j2objc:j2objc-annotations:jar:1.1:compile
      [INFO] |  |     \\- org.codehaus.mojo:animal-sniffer-annotations:jar:1.14:compile
      [INFO] |  +- org.codehaus.plexus:plexus-utils:jar:3.3.0:compile
      [INFO] |  +- org.codehaus.plexus:plexus-classworlds:jar:2.6.0:compile
      [INFO] |  +- org.codehaus.plexus:plexus-interpolation:jar:1.26:compile
      [INFO] |  +- org.codehaus.plexus:plexus-component-annotations:jar:2.1.0:compile
      [INFO] |  \\- org.apache.commons:commons-lang3:jar:3.8.1:compile
      [INFO] +- org.apache.maven.wagon:wagon-provider-api:jar:3.5.2:compile
      [INFO] \\- org.apache.maven.archetype:archetype-common:jar:3.2.1:compile
      [INFO]    +- org.apache.maven.archetype:archetype-catalog:jar:3.2.1:compile
      [INFO]    +- org.apache.maven.archetype:archetype-descriptor:jar:3.2.1:compile
      [INFO]    +- org.codehaus.groovy:groovy-all:jar:2.4.16:compile
      [INFO]    +- org.apache.ivy:ivy:jar:2.5.0:runtime
      [INFO]    +- org.jdom:jdom2:jar:2.0.6:compile
      [INFO]    +- org.apache.maven.shared:maven-invoker:jar:3.0.1:compile
      [INFO]    +- org.apache.maven:maven-aether-provider:jar:3.0:runtime
      [INFO]    |  +- org.sonatype.aether:aether-api:jar:1.7:runtime
      [INFO]    |  +- org.sonatype.aether:aether-util:jar:1.7:runtime
      [INFO]    |  \\- org.sonatype.aether:aether-impl:jar:1.7:runtime
      [INFO]    |     \\- org.sonatype.aether:aether-spi:jar:1.7:runtime
      [INFO]    +- org.apache.maven.shared:maven-artifact-transfer:jar:0.13.1:compile
      [INFO]    |  \\- org.apache.maven.shared:maven-common-artifact-filters:jar:3.1.0:compile
      [INFO]    |     \\- org.sonatype.sisu:sisu-inject-plexus:jar:1.4.2:compile
      [INFO]    |        \\- org.sonatype.sisu:sisu-inject-bean:jar:1.4.2:compile
      [INFO]    |           \\- org.sonatype.sisu:sisu-guice:jar:noaop:2.1.7:compile
      [INFO]    +- commons-io:commons-io:jar:2.6:compile
      [INFO]    +- commons-collections:commons-collections:jar:3.2.2:compile
      [INFO]    +- org.codehaus.plexus:plexus-velocity:jar:1.2:compile
      [INFO]    +- org.apache.velocity:velocity:jar:1.7:compile
      [INFO]    |  \\- commons-lang:commons-lang:jar:2.4:compile
      [INFO]    \\- com.ibm.icu:icu4j:jar:70.1:compile
      """;

  private final Sdk myJdk;
  private final String myOptions;
  private final MavenDistribution myDistribution;
  private final Integer myDebugPort;

  public MavenIndexerCMDState(Sdk jdk, String vmOptions, MavenDistribution distribution, Integer debugPort) {
    super(null);
    myJdk = jdk;
    myOptions = vmOptions;
    myDistribution = distribution;
    myDebugPort = debugPort;
  }

  @Override
  protected @NotNull ProcessHandler startProcess() throws ExecutionException {
    SimpleJavaParameters params = createJavaParameters();
    GeneralCommandLine commandLine = params.toCommandLine();
    OSProcessHandler processHandler = new OSProcessHandler.Silent(commandLine);
    processHandler.setShouldDestroyProcessRecursively(false);
    return processHandler;
  }

  protected SimpleJavaParameters createJavaParameters() {
    final SimpleJavaParameters params = new SimpleJavaParameters();

    params.setJdk(myJdk);
    params.setWorkingDirectory(PathManager.getBinPath());
    params.getVMParametersList().add(Registry.stringValue("maven.dedicated.indexer.vmargs"));
    if (myDebugPort != null) {
      params.getVMParametersList()
        .addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:" + myDebugPort);
    }
    params.getVMParametersList().add("-Didea.version=" + MavenUtil.getIdeaVersionToPassToMavenProcess());
    params.setMainClass("org.jetbrains.idea.maven.server.indexer.MavenServerIndexerMain");
    params.getClassPath().add(PathUtil.getJarPathForClass(StringUtilRt.class));//util-rt
    params.getClassPath().add(PathUtil.getJarPathForClass(NotNull.class));//annotations-java5
    params.getClassPath().add(PathUtil.getJarPathForClass(Element.class));//JDOM
    params.getClassPath().addAllFiles(collectClassPathAndLibsFolder(myDistribution));
    addDependencies(params.getClassPath());
    return params;
  }

  private static void addDependencies(PathsList classPath) {
    String[] dependencies = dependenciesOutput.split("\\n");
    Pattern format = Pattern.compile(
      "^\\[INFO\\].*-\\s(?<groupId>[0-9a-z._\\-]+):(?<artifactId>[0-9a-z._\\-]+):jar:?(?<classifier>[a-z_]*):(?<version>[0-9a-z._\\-]+):(?<scope>(compile|runtime))$");

    PathMacros pathMacros = PathMacros.getInstance();
    String path = pathMacros.getValue(PathMacrosImpl.MAVEN_REPOSITORY);
    File mavenRepository = path == null ? new File(new File(PathManager.getHomePath(), ".m2"), "repository") : new File(path);
    for (String dep : dependencies) {
      Matcher matcher = format.matcher(dep);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(dep);
      }
      @NotNull String groupId = matcher.group("groupId");
      @NotNull String artifactId = matcher.group("artifactId");
      @Nullable String classifier = StringUtils.trimToNull(matcher.group("classifier"));
      @NotNull String version = matcher.group("version");
      MavenId mavenId = new MavenId(groupId, artifactId, version);
      File jar = MavenUtil.makeLocalRepositoryFile(mavenId, mavenRepository, "jar", classifier);
      if (!jar.isFile()) throw new IllegalStateException("File " + jar.getPath() + " not found");
      classPath.add(jar);
    }
  }

  private static @NotNull List<File> collectClassPathAndLibsFolder(@NotNull MavenDistribution distribution) {
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();

    final List<File> classpath = new ArrayList<>();

    if (pluginFileOrDir.isDirectory()) {
      MavenLog.LOG.debug("collecting classpath for local run");
      prepareClassPathForLocalRunAndUnitTests(distribution.getVersion(), classpath, root);
    }
    else {
      MavenLog.LOG.debug("collecting classpath for production");
      prepareClassPathForProduction(distribution.getVersion(), classpath, root);
    }

    addMavenLibs(classpath, distribution.getMavenHome().toFile());
    MavenLog.LOG.debug("Collected classpath = ", classpath);
    return classpath;
  }

  private static void prepareClassPathForProduction(@NotNull String mavenVersion,
                                                    @NotNull List<File> classpath,
                                                    String root) {
    classpath.add(new File(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(new File(PathUtil.getJarPathForClass(MavenServer.class)));
    classpath.add(new File(root, "maven-server-indexer.jar"));
    addDir(classpath, new File(root, "maven-server-indexer"), f -> true);
  }

  private static void prepareClassPathForLocalRunAndUnitTests(@NotNull String mavenVersion, List<File> classpath, String root) {
    classpath.add(new File(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(new File(root, "intellij.maven.server"));
    classpath.add(new File(root, "intellij.maven.server.indexer"));
  }

  private static void addMavenLibs(List<File> classpath, File mavenHome) {
    addDir(classpath, new File(mavenHome, "lib"), f -> true);
    File bootFolder = new File(mavenHome, "boot");
    File[] classworldsJars = bootFolder.listFiles((dir, name) -> StringUtil.contains(name, "classworlds"));
    if (classworldsJars != null) {
      Collections.addAll(classpath, classworldsJars);
    }
  }

  private static void addDir(List<File> classpath, File dir, Predicate<File> filter) {
    File[] files = dir.listFiles();
    if (files == null) return;

    for (File jar : files) {
      if (jar.isFile() && jar.getName().endsWith(".jar") && filter.test(jar)) {
        classpath.add(jar);
      }
    }
  }
}
