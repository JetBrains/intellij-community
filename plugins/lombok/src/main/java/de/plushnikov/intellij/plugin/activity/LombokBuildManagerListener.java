package de.plushnikov.intellij.plugin.activity;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import java.util.UUID;

public class LombokBuildManagerListener implements BuildManagerListener {

  @Override
  public void beforeBuildProcessStarted(@NotNull Project project,
                                        @NotNull UUID sessionId) {
    if (!hasAnnotationProcessorsEnabled(project) &&
        ReadAction.compute(() -> LombokLibraryUtil.hasLombokLibrary(project))) {
      enableAnnotationProcessors(project);
    }
  }

  private CompilerConfigurationImpl getCompilerConfiguration(@NotNull Project project) {
    return (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
  }

  private boolean hasAnnotationProcessorsEnabled(@NotNull Project project) {
    final CompilerConfigurationImpl compilerConfiguration = getCompilerConfiguration(project);
    return compilerConfiguration.getDefaultProcessorProfile().isEnabled() &&
           compilerConfiguration.getModuleProcessorProfiles().stream().allMatch(AnnotationProcessingConfiguration::isEnabled);
  }

  private void enableAnnotationProcessors(@NotNull Project project) {
    CompilerConfigurationImpl compilerConfiguration = getCompilerConfiguration(project);
    compilerConfiguration.getDefaultProcessorProfile().setEnabled(true);
    compilerConfiguration.getModuleProcessorProfiles().forEach(pp -> pp.setEnabled(true));
  }
}
