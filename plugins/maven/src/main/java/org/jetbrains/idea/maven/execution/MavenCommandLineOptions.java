// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import java.util.*;

/**
 * @author ibessonov
 */
public class MavenCommandLineOptions {

  private static final Map<String, Option> ourOptionsIndexMap;
  private static final Set<Option> ourAllOptions;

  static {
    ourAllOptions = new HashSet<>();

    ourAllOptions.add(new Option("-am", "--also-make", "If project list is specified, also build projects required by the list"));
    ourAllOptions.add(new Option("-amd", "--also-make-dependents", "If project list is specified, also build projects that depend on projects on the list"));
    ourAllOptions.add(new Option("-B", "--batch-mode", "Run in non-interactive (batch) mode (disables output color)"));
    ourAllOptions.add(new Option("-b", "--builder", "The id of the build strategy to use"));
    ourAllOptions.add(new Option("-C", "--strict-checksums", "Fail the build if checksums don't match"));
    ourAllOptions.add(new Option("-c", "--lax-checksums", "Warn if checksums don't match"));
    ourAllOptions.add(new Option("-cpu", "--check-plugin-updates", "Ineffective, only kept for backward compatibility"));
    ourAllOptions.add(new Option("-D", "--define", "Define a system property"));
    ourAllOptions.add(new Option("-e", "--errors", "Produce execution error messages"));
    ourAllOptions.add(new Option("-emp", "--encrypt-master-password", "Encrypt master security password"));
    ourAllOptions.add(new Option("-ep", "--encrypt-password", "Encrypt server password"));
    ourAllOptions.add(new Option("-f", "--file", "Force the use of an alternate POM file (or directory with pom.xml)"));
    ourAllOptions.add(new Option("-fae", "--fail-at-end", "Only fail the build afterwards; allow all non-impacted builds to continue"));
    ourAllOptions.add(new Option("-ff", "--fail-fast", "Stop at first failure in reactorized builds"));
    ourAllOptions.add(new Option("-fn", "--fail-never", "NEVER fail the build, regardless of project result"));
    ourAllOptions.add(new Option("-gs", "--global-settings", "Alternate path for the global settings file"));
    ourAllOptions.add(new Option("-gt", "--global-toolchains", "Alternate path for the global toolchains file"));
    ourAllOptions.add(new Option("-h", "--help", "Display help information"));
    ourAllOptions.add(new Option("-l", "--log-file", "Log file where all build output will go (disables output color)"));
    ourAllOptions.add(new Option("-llr", "--legacy-local-repository", "Use Maven 2 Legacy Local Repository behaviour, ie no use of _remote.repositories. Can also be activated by using -Dmaven.legacyLocalRepo=true"));
    ourAllOptions.add(new Option("-N", "--non-recursive", "Do not recurse into sub-projects"));
    ourAllOptions.add(new Option("-npr", "--no-plugin-registry", "Ineffective, only kept for backward compatibility"));
    ourAllOptions.add(new Option("-npu", "--no-plugin-updates", "Ineffective, only kept for backward compatibility"));
    ourAllOptions.add(new Option("-nsu", "--no-snapshot-updates", "Suppress SNAPSHOT updates"));
    ourAllOptions.add(new Option("-o", "--offline", "Work offline"));
    ourAllOptions.add(new Option("-P", "--activate-profiles", "Comma-delimited list of profiles to activate"));
    ourAllOptions.add(new Option("-pl", "--projects", "Comma-delimited list of specified reactor projects to build instead of all projects. A project can be specified by [groupId]:artifactId or by its relative path"));
    ourAllOptions.add(new Option("-q", "--quiet", "Quiet output - only show errors"));
    ourAllOptions.add(new Option("-rf", "--resume-from", "Resume reactor from specified project"));
    ourAllOptions.add(new Option("-s", "--settings", "Alternate path for the user settings file"));
    ourAllOptions.add(new Option("-t", "--toolchains", "Alternate path for the user toolchains file"));
    ourAllOptions.add(new Option("-T", "--threads", "Thread count, for instance 2.0C where C is core multiplied"));
    ourAllOptions.add(new Option("-U", "--update-snapshots", "Forces a check for missing releases and updated snapshots on remote repositories"));
    ourAllOptions.add(new Option("-up", "--update-plugins", "Ineffective, only kept for backward compatibility"));
    ourAllOptions.add(new Option("-v", "--version", "Display version information"));
    ourAllOptions.add(new Option("-V", "--show-version", "Display version information WITHOUT stopping build"));
    ourAllOptions.add(new Option("-X", "--debug", "Produce execution debug output"));

    ourOptionsIndexMap = new HashMap<>();
    for (Option option : ourAllOptions) {
      ourOptionsIndexMap.put(option.getName(false), option);
      ourOptionsIndexMap.put(option.getName(true), option);
    }
  }

  public static class Option {
    private final String myName;
    private final String myLongName;
    private final String myDescription;

    public Option(String name, String longName, String description) {
      myName = name;
      myLongName = longName;
      myDescription = description;
    }

    public String getName(boolean longName) {
      return longName? myLongName : myName;
    }

    public String getDescription() {
      return myDescription;
    }
  }

  public static Collection<Option> getAllOptions() {
    return Collections.unmodifiableSet(ourAllOptions);
  }

  public static Option findOption(String name) {
    return ourOptionsIndexMap.get(name);
  }
}
