package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class SSBasedInspectionCompiledPatternsCache implements StartupActivity {
  private static final Key<MatcherImpl.CompiledOptions> COMPILED_OPTIONS_KEY = Key.create("SSR_INSPECTION_COMPILED_OPTIONS_KEY");

  @Override
  public void runActivity(@NotNull final Project project) {
     precompileConfigurations(project, null);
  }

  static void precompileConfigurations(final Project project, @Nullable final SSBasedInspection ssBasedInspection) {
    if (project.isDisposed()) {
      return;
    }
    final MatcherImpl.CompiledOptions currentCompiledOptions = getCompiledOptions(project);

    final SSBasedInspection inspection = ssBasedInspection != null ? ssBasedInspection : getInspection(project);
    if (inspection == null) {
      return;
    }

    List<Configuration> configurations = inspection.getConfigurations();
    if (configurations == null) {
      configurations = Collections.emptyList();
    }

    if ((currentCompiledOptions == null || currentCompiledOptions.getMatchContexts().isEmpty()) &&
        configurations.isEmpty()) {
      return;
    }

    final Matcher matcher = new Matcher(project);
    final MatcherImpl.CompiledOptions compiledOptions = matcher.precompileOptions(configurations);

    if (compiledOptions != null) {
      project.putUserData(COMPILED_OPTIONS_KEY, compiledOptions);
    }
  }

  @Nullable
  private static SSBasedInspection getInspection(@NotNull Project project) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final InspectionToolWrapper entry = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, project);

    return entry == null ? null : (SSBasedInspection)entry.getTool();
  }

  @Nullable
  static MatcherImpl.CompiledOptions getCompiledOptions(@NotNull Project project) {
    return project.getUserData(COMPILED_OPTIONS_KEY);
  }

  @TestOnly
  static void setCompiledOptions(@NotNull Project project, @NotNull List<Configuration> configurations) {
    final Matcher matcher = new Matcher(project);
    project.putUserData(COMPILED_OPTIONS_KEY,
                        matcher.precompileOptions(configurations));
  }
}
