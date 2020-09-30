// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.externalSystem.service.execution.cmd.CommandLineCompletionProvider;
import com.intellij.openapi.project.Project;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings("AccessStaticViaInstance")
public class MavenArgumentsCompletionProvider extends CommandLineCompletionProvider {

  private static final Options ourOptions;

  static {
    // Copy pasted from org.apache.maven.cli.CLIManager.<init>()

    Options options = new Options();
    options.addOption(OptionBuilder.withLongOpt("help").withDescription(RunnerBundle.message("maven.options.description.h")).create('h'));
    options.addOption(
      OptionBuilder.withLongOpt("file").hasArg().withDescription(RunnerBundle.message("maven.options.description.f")).create('f'));
    options.addOption(
      OptionBuilder.withLongOpt("define").hasArg().withDescription(RunnerBundle.message("maven.options.description.D")).create('D'));
    options.addOption(OptionBuilder.withLongOpt("offline").withDescription(
      RunnerBundle.message("maven.options.description.o")).create('o'));
    options.addOption(OptionBuilder.withLongOpt("version").withDescription(
      RunnerBundle.message("maven.options.description.v")).create('v'));
    options.addOption(OptionBuilder.withLongOpt("quiet").withDescription(
      RunnerBundle.message("maven.options.description.q")).create('q'));
    options.addOption(OptionBuilder.withLongOpt("debug").withDescription(
      RunnerBundle.message("maven.options.description.X")).create('X'));
    options.addOption(OptionBuilder.withLongOpt("errors").withDescription(
      RunnerBundle.message("maven.options.description.e")).create('e'));
    options.addOption(OptionBuilder.withLongOpt("non-recursive").withDescription(
      RunnerBundle.message("maven.options.description.N")).create('N'));
    options.addOption(OptionBuilder.withLongOpt("update-snapshots")
                        .withDescription(MavenConfigurableBundle.message("maven.settings.general.update.snapshots.tooltip")).create('U'));
    options.addOption(
      OptionBuilder.withLongOpt("activate-profiles").withDescription(RunnerBundle.message("maven.options.description.P")).hasArg()
        .create('P'));
    options.addOption(
      OptionBuilder.withLongOpt("batch-mode").withDescription(RunnerBundle.message("maven.options.description.B")).create('B'));
    options.addOption(OptionBuilder.withLongOpt("no-snapshot-updates").withDescription(
      RunnerBundle.message("maven.options.description.nsu")).create("nsu"));
    options.addOption(OptionBuilder.withLongOpt("strict-checksums").withDescription(
      RunnerBundle.message("maven.options.description.C")).create('C'));
    options.addOption(OptionBuilder.withLongOpt("lax-checksums").withDescription(
      RunnerBundle.message("maven.options.description.c")).create('c'));
    options.addOption(
      OptionBuilder.withLongOpt("settings").withDescription(RunnerBundle.message("maven.options.description.s")).hasArg().create('s'));
    options.addOption(
      OptionBuilder.withLongOpt("global-settings").withDescription(RunnerBundle.message("maven.options.description.gs")).hasArg()
        .create("gs"));
    options
      .addOption(
        OptionBuilder.withLongOpt("toolchains").withDescription(RunnerBundle.message("maven.options.description.t")).hasArg().create('t'));
    options
      .addOption(OptionBuilder.withLongOpt("fail-fast").withDescription(RunnerBundle.message("maven.options.description.ff")).create("ff"));
    options.addOption(
      OptionBuilder.withLongOpt("fail-at-end").withDescription(RunnerBundle.message("maven.options.description.fae"))
        .create("fae"));
    options
      .addOption(
        OptionBuilder.withLongOpt("fail-never").withDescription(RunnerBundle.message("maven.options.description.fn")).create("fn"));
    options.addOption(OptionBuilder.withLongOpt("resume-from").hasArg().withDescription(
      RunnerBundle.message("maven.options.description.rf")).create("rf"));
    options.addOption(OptionBuilder.withLongOpt("projects").withDescription(
      RunnerBundle.message("maven.options.description.pl"))
                        .hasArg().create("pl"));
    options.addOption(
      OptionBuilder.withLongOpt("also-make").withDescription(RunnerBundle.message("maven.options.description.am"))
        .create("am"));
    options.addOption(OptionBuilder.withLongOpt("also-make-dependents")
                        .withDescription(RunnerBundle.message("maven.options.description.amd")).create("amd"));
    options.addOption(
      OptionBuilder.withLongOpt("log-file").hasArg().withDescription(RunnerBundle.message("maven.options.description.l")).create("l"));
    options
      .addOption(
        OptionBuilder.withLongOpt("show-version").withDescription(RunnerBundle.message("maven.options.description.V")).create('V'));
    options
      .addOption(OptionBuilder.withLongOpt("encrypt-master-password").hasArg().withDescription(
        RunnerBundle.message("maven.options.description.emp")).create("emp"));
    options.addOption(OptionBuilder.withLongOpt("encrypt-password").hasArg().withDescription(
      RunnerBundle.message("maven.options.description.ep")).create("ep"));
    options.addOption(
      OptionBuilder.withLongOpt("threads").hasArg().withDescription(RunnerBundle.message("maven.options.description.T")).create("T"));

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
