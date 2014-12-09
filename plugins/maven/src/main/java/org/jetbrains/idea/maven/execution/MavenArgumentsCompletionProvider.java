package org.jetbrains.idea.maven.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.externalSystem.service.execution.cmd.CommandLineCompletionProvider;
import com.intellij.openapi.project.Project;
import groovyjarjarcommonscli.OptionBuilder;
import groovyjarjarcommonscli.Options;
import org.jetbrains.annotations.NotNull;
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
    options.addOption(OptionBuilder.withLongOpt("help").withDescription("Display help information").create('h'));
    options.addOption(
      OptionBuilder.withLongOpt("file").hasArg().withDescription("Force the use of an alternate POM file (or directory with pom.xml).")
        .create(
          'f'));
    options.addOption(OptionBuilder.withLongOpt("define").hasArg().withDescription("Define a system property").create('D'));
    options.addOption(OptionBuilder.withLongOpt("offline").withDescription("Work offline").create('o'));
    options.addOption(OptionBuilder.withLongOpt("version").withDescription("Display version information").create('v'));
    options.addOption(OptionBuilder.withLongOpt("quiet").withDescription("Quiet output - only show errors").create('q'));
    options.addOption(OptionBuilder.withLongOpt("debug").withDescription("Produce execution debug output").create('X'));
    options.addOption(OptionBuilder.withLongOpt("errors").withDescription("Produce execution error messages").create('e'));
    options.addOption(OptionBuilder.withLongOpt("non-recursive").withDescription("Do not recurse into sub-projects").create('N'));
    options.addOption(OptionBuilder.withLongOpt("update-snapshots")
                        .withDescription("Forces a check for updated releases and snapshots on remote repositories").create(
        'U'));
    options.addOption(
      OptionBuilder.withLongOpt("activate-profiles").withDescription("Comma-delimited list of profiles to activate").hasArg().create(
        'P'));
    options.addOption(OptionBuilder.withLongOpt("batch-mode").withDescription("Run in non-interactive (batch) mode").create('B'));
    options.addOption(OptionBuilder.withLongOpt("no-snapshot-updates").withDescription("Suppress SNAPSHOT updates").create("nsu"));
    options.addOption(OptionBuilder.withLongOpt("strict-checksums").withDescription("Fail the build if checksums don't match").create(
      'C'));
    options.addOption(OptionBuilder.withLongOpt("lax-checksums").withDescription("Warn if checksums don't match").create('c'));
    options.addOption(OptionBuilder.withLongOpt("settings").withDescription("Alternate path for the user settings file").hasArg().create(
      's'));
    options.addOption(
      OptionBuilder.withLongOpt("global-settings").withDescription("Alternate path for the global settings file").hasArg().create(
        "gs"));
    options
      .addOption(OptionBuilder.withLongOpt("toolchains").withDescription("Alternate path for the user toolchains file").hasArg().create(
        't'));
    options.addOption(OptionBuilder.withLongOpt("fail-fast").withDescription("Stop at first failure in reactorized builds").create(
      "ff"));
    options.addOption(
      OptionBuilder.withLongOpt("fail-at-end").withDescription("Only fail the build afterwards; allow all non-impacted builds to continue")
        .create(
          "fae"));
    options.addOption(OptionBuilder.withLongOpt("fail-never").withDescription("NEVER fail the build, regardless of project result").create(
      "fn"));
    options.addOption(OptionBuilder.withLongOpt("resume-from").hasArg().withDescription("Resume reactor from specified project").create(
      "rf"));
    options.addOption(OptionBuilder.withLongOpt("projects").withDescription(
      "Comma-delimited list of specified reactor projects to build instead of all projects. A project can be specified by [groupId]:artifactId or by its relative path.")
                        .hasArg().create(
        "pl"));
    options.addOption(
      OptionBuilder.withLongOpt("also-make").withDescription("If project list is specified, also build projects required by the list")
        .create(
          "am"));
    options.addOption(OptionBuilder.withLongOpt("also-make-dependents")
                        .withDescription("If project list is specified, also build projects that depend on projects on the list").create(
        "amd"));
    options.addOption(OptionBuilder.withLongOpt("log-file").hasArg().withDescription("Log file to where all build output will go.").create(
      "l"));
    options
      .addOption(OptionBuilder.withLongOpt("show-version").withDescription("Display version information WITHOUT stopping build").create(
        'V'));
    options
      .addOption(OptionBuilder.withLongOpt("encrypt-master-password").hasArg().withDescription("Encrypt master security password").create(
        "emp"));
    options.addOption(OptionBuilder.withLongOpt("encrypt-password").hasArg().withDescription("Encrypt server password").create(
      "ep"));
    options.addOption(
      OptionBuilder.withLongOpt("threads").hasArg().withDescription("Thread count, for instance 2.0C where C is core multiplied").create(
        "T"));

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
