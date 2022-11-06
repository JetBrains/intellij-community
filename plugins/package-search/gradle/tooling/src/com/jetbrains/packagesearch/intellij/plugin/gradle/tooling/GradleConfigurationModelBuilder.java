package com.jetbrains.packagesearch.intellij.plugin.gradle.tooling;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.ArrayList;
import java.util.List;

public class GradleConfigurationModelBuilder extends AbstractModelBuilderService {

    @Override
    public GradleConfigurationReportModel buildAll(@NotNull String modelName, Project project, @NotNull ModelBuilderContext context) {

        List<GradleConfigurationReportModel.Configuration> configurations =
                new ArrayList<>(project.getConfigurations().size());

        for (Configuration configuration : project.getConfigurations()) {

            List<GradleConfigurationReportModel.Dependency> dependencies =
                    new ArrayList<>(configuration.getDependencies().size());

            for (Dependency dependency : configuration.getDependencies()) {
                String group = dependency.getGroup();
                String version = dependency.getVersion();

                if (group == null || version == null) continue;

                dependencies.add(new GradleConfigurationReportModelImpl.DependencyImpl(group, dependency.getName(), version));
            }
            configurations.add(new GradleConfigurationReportModelImpl.ConfigurationImpl(configuration.getName(), dependencies));
        }

        return new GradleConfigurationReportModelImpl(project.getProjectDir().getAbsolutePath(), configurations);
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(GradleConfigurationReportModel.class.getName());
    }

    @NotNull
    @Override
    public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
        return ErrorMessageBuilder
                .create(project, e, "Gradle import errors")
                .withDescription("Unable to import resolved versions " +
                        "from configurations in project ''${project.name}'' for" +
                        " the Dependencies toolwindow.");
    }
}
