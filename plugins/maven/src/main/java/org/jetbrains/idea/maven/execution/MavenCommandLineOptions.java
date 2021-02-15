// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author ibessonov
 */
public final class MavenCommandLineOptions {

  private static final Map<String, Option> ourOptionsIndexMap;
  private static final Set<Option> ourAllOptions;

  static {
    ourAllOptions = new HashSet<>();

    ourAllOptions.add(new Option("-am", "--also-make", RunnerBundle.message("maven.options.description.am")));
    ourAllOptions.add(new Option("-amd", "--also-make-dependents", RunnerBundle.message("maven.options.description.amd")));
    ourAllOptions.add(new Option("-B", "--batch-mode", RunnerBundle.message("maven.options.description.B")));
    ourAllOptions.add(new Option("-b", "--builder", RunnerBundle.message("maven.options.description.b")));
    ourAllOptions.add(new Option("-C", "--strict-checksums", RunnerBundle.message("maven.options.description.C")));
    ourAllOptions.add(new Option("-c", "--lax-checksums", RunnerBundle.message("maven.options.description.c")));
    ourAllOptions.add(new Option("-cpu", "--check-plugin-updates", RunnerBundle.message("maven.options.description.cpu")));
    ourAllOptions.add(new Option("-D", "--define", RunnerBundle.message("maven.options.description.D")));
    ourAllOptions.add(new Option("-e", "--errors", RunnerBundle.message("maven.options.description.e")));
    ourAllOptions.add(new Option("-emp", "--encrypt-master-password", RunnerBundle.message("maven.options.description.emp")));
    ourAllOptions.add(new Option("-ep", "--encrypt-password", RunnerBundle.message("maven.options.description.ep")));
    ourAllOptions.add(new Option("-f", "--file", RunnerBundle.message("maven.options.description.f")));
    ourAllOptions.add(new Option("-fae", "--fail-at-end", RunnerBundle.message("maven.options.description.fae")));
    ourAllOptions.add(new Option("-ff", "--fail-fast", RunnerBundle.message("maven.options.description.ff")));
    ourAllOptions.add(new Option("-fn", "--fail-never", RunnerBundle.message("maven.options.description.fn")));
    ourAllOptions.add(new Option("-gs", "--global-settings", RunnerBundle.message("maven.options.description.gs")));
    ourAllOptions.add(new Option("-gt", "--global-toolchains", RunnerBundle.message("maven.options.description.gt")));
    ourAllOptions.add(new Option("-h", "--help", RunnerBundle.message("maven.options.description.h")));
    ourAllOptions.add(new Option("-l", "--log-file", RunnerBundle.message("maven.options.description.l")));
    ourAllOptions.add(new Option("-llr", "--legacy-local-repository", RunnerBundle.message("maven.options.description.llr")));
    ourAllOptions.add(new Option("-N", "--non-recursive", RunnerBundle.message("maven.options.description.N")));
    ourAllOptions.add(new Option("-npr", "--no-plugin-registry", RunnerBundle.message("maven.options.description.npr")));
    ourAllOptions.add(new Option("-npu", "--no-plugin-updates", RunnerBundle.message("maven.options.description.npu")));
    ourAllOptions.add(new Option("-nsu", "--no-snapshot-updates", RunnerBundle.message("maven.options.description.nsu")));
    ourAllOptions.add(new Option("-o", "--offline", RunnerBundle.message("maven.options.description.o")));
    ourAllOptions.add(new Option("-P", "--activate-profiles", RunnerBundle.message("maven.options.description.P")));
    ourAllOptions.add(new Option("-pl", "--projects", RunnerBundle.message("maven.options.description.pl")));
    ourAllOptions.add(new Option("-q", "--quiet", RunnerBundle.message("maven.options.description.q")));
    ourAllOptions.add(new Option("-rf", "--resume-from", RunnerBundle.message("maven.options.description.rf")));
    ourAllOptions.add(new Option("-s", "--settings", RunnerBundle.message("maven.options.description.s")));
    ourAllOptions.add(new Option("-t", "--toolchains", RunnerBundle.message("maven.options.description.t")));
    ourAllOptions.add(new Option("-T", "--threads", RunnerBundle.message("maven.options.description.T")));
    ourAllOptions.add(new Option("-U", "--update-snapshots", RunnerBundle.message("maven.options.description.U")));
    ourAllOptions.add(new Option("-up", "--update-plugins", RunnerBundle.message("maven.options.description.up")));
    ourAllOptions.add(new Option("-v", "--version", RunnerBundle.message("maven.options.description.v")));
    ourAllOptions.add(new Option("-V", "--show-version", RunnerBundle.message("maven.options.description.V")));
    ourAllOptions.add(new Option("-X", "--debug", RunnerBundle.message("maven.options.description.X")));

    ourOptionsIndexMap = new HashMap<>();
    for (Option option : ourAllOptions) {
      ourOptionsIndexMap.put(option.getName(false), option);
      ourOptionsIndexMap.put(option.getName(true), option);
    }
  }

  public static class Option {
    private final String myName;
    private final String myLongName;
    @Nls(capitalization = Nls.Capitalization.Sentence)
    private final String myDescription;

    public Option(@NonNls String name, @NonNls String longName, @Nls(capitalization = Nls.Capitalization.Sentence) String description) {
      myName = name;
      myLongName = longName;
      myDescription = description;
    }

    public String getName(boolean longName) {
      return longName? myLongName : myName;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
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
