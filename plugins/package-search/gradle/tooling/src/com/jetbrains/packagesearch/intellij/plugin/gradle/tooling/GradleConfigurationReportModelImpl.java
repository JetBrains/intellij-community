package com.jetbrains.packagesearch.intellij.plugin.gradle.tooling;

import java.util.List;

public class GradleConfigurationReportModelImpl implements GradleConfigurationReportModel {

    private final String projectDir;
    private final List<Configuration> configurations;

    public GradleConfigurationReportModelImpl(String projectDir, List<Configuration> configurations) {
        this.projectDir = projectDir;
        this.configurations = configurations;
    }

    @Override
    public String getProjectDir() {
        return projectDir;
    }

    @Override
    public List<Configuration> getConfigurations() {
        return configurations;
    }

    static public class DependencyImpl implements GradleConfigurationReportModel.Dependency {

        private final String group;
        private final String artifact;
        private final String version;

        public DependencyImpl(String group, String artifact, String version) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }

        @Override
        public String getGroupId() {
            return group;
        }

        @Override
        public String getArtifactId() {
            return artifact;
        }

        @Override
        public String getVersion() {
            return version;
        }
    }

    static public class ConfigurationImpl implements GradleConfigurationReportModel.Configuration {

        private final String name;
        private final List<Dependency> dependencies;

        public ConfigurationImpl(String name, List<Dependency> dependencies) {
            this.name = name;
            this.dependencies = dependencies;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Dependency> getDependencies() {
            return dependencies;
        }
    }
}
