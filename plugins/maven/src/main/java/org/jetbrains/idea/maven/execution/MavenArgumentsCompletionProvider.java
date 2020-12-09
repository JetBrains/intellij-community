// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.externalSystem.service.execution.cmd.CommandLineCompletionProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenArgumentsCompletionProvider extends CommandLineCompletionProvider implements DumbAware {

  private static final Options ourOptions;

  static {
    // Copy pasted from org.apache.maven.cli.CLIManager.<init>()

    Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc(RunnerBundle.message("maven.options.description.h")).build());
    options.addOption(Option.builder("f").longOpt("file").hasArg().desc(RunnerBundle.message("maven.options.description.f")).build());
    options.addOption(Option.builder("D").longOpt("define").hasArg().desc(RunnerBundle.message("maven.options.description.D")).build());
    options.addOption(Option.builder("o").longOpt("offline").desc(RunnerBundle.message("maven.options.description.o")).build());
    options.addOption(Option.builder("v").longOpt("version").desc(RunnerBundle.message("maven.options.description.v")).build());
    options.addOption(Option.builder("q").longOpt("quiet").desc(RunnerBundle.message("maven.options.description.q")).build());
    options.addOption(Option.builder("X").longOpt("debug").desc(RunnerBundle.message("maven.options.description.X")).build());
    options.addOption(Option.builder("e").longOpt("errors").desc(RunnerBundle.message("maven.options.description.e")).build());
    options.addOption(Option.builder("N").longOpt("non-recursive").desc(RunnerBundle.message("maven.options.description.N")).build());
    options.addOption(Option.builder("U").longOpt("update-snapshots")
                        .desc(MavenConfigurableBundle.message("maven.settings.general.update.snapshots.tooltip")).build());
    options.addOption(
      Option.builder("P").longOpt("activate-profiles").desc(RunnerBundle.message("maven.options.description.P")).hasArg().build());
    options.addOption(Option.builder("B").longOpt("batch-mode").desc(RunnerBundle.message("maven.options.description.B")).build());
    options
      .addOption(Option.builder("nsu").longOpt("no-snapshot-updates").desc(RunnerBundle.message("maven.options.description.nsu")).build());
    options.addOption(Option.builder("C").longOpt("strict-checksums").desc(RunnerBundle.message("maven.options.description.C")).build());
    options.addOption(Option.builder("c").longOpt("lax-checksums").desc(RunnerBundle.message("maven.options.description.c")).build());
    options.addOption(Option.builder("s").longOpt("settings").desc(RunnerBundle.message("maven.options.description.s")).hasArg().build());
    options.addOption(
      Option.builder("gs").longOpt("global-settings").desc(RunnerBundle.message("maven.options.description.gs")).hasArg().build());
    options.addOption(Option.builder("t").longOpt("toolchains").desc(RunnerBundle.message("maven.options.description.t")).hasArg().build());
    options.addOption(Option.builder("ff").longOpt("fail-fast").desc(RunnerBundle.message("maven.options.description.ff")).build());
    options.addOption(Option.builder("fae").longOpt("fail-at-end").desc(RunnerBundle.message("maven.options.description.fae")).build());
    options.addOption(Option.builder("fn").longOpt("fail-never").desc(RunnerBundle.message("maven.options.description.fn")).build());
    options
      .addOption(Option.builder("rf").longOpt("resume-from").hasArg().desc(RunnerBundle.message("maven.options.description.rf")).build());
    options.addOption(Option.builder("pl").longOpt("projects").desc(RunnerBundle.message("maven.options.description.pl")).hasArg().build());
    options.addOption(Option.builder("am").longOpt("also-make").desc(RunnerBundle.message("maven.options.description.am")).build());
    options
      .addOption(Option.builder("amd").longOpt("also-make-dependents").desc(RunnerBundle.message("maven.options.description.amd")).build());
    options.addOption(Option.builder("l").longOpt("log-file").hasArg().desc(RunnerBundle.message("maven.options.description.l")).build());
    options.addOption(Option.builder("V").longOpt("show-version").desc(RunnerBundle.message("maven.options.description.V")).build());
    options.addOption(
      Option.builder("emp").longOpt("encrypt-master-password").hasArg().desc(RunnerBundle.message("maven.options.description.emp"))
        .build());
    options.addOption(
      Option.builder("ep").longOpt("encrypt-password").hasArg().desc(RunnerBundle.message("maven.options.description.ep")).build());
    options.addOption(Option.builder("T").longOpt("threads").hasArg().desc(RunnerBundle.message("maven.options.description.T")).build());

    ourOptions = options;
  }

  private volatile List<LookupElement> myCachedElements;
  private final Project myProject;


  public MavenArgumentsCompletionProvider(@NotNull Project project) {
    super(ourOptions);
    myProject = project;
  }

  @Override
  protected void addArgumentVariants(@NotNull CompletionResultSet result) {
    List<LookupElement> cachedElements = myCachedElements;
    if (cachedElements == null) {
      cachedElements = MavenUtil.getPhaseVariants(MavenProjectsManager.getInstance(myProject));

      myCachedElements = cachedElements;
    }

    result.addAllElements(cachedElements);
  }
}
