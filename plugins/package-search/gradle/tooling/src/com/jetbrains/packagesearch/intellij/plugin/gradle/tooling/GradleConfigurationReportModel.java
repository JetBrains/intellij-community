package com.jetbrains.packagesearch.intellij.plugin.gradle.tooling;

import java.io.Serializable;
import java.util.List;

public interface GradleConfigurationReportModel extends Serializable {

    String getProjectDir();
    List<Configuration> getConfigurations();

    interface Configuration extends Serializable {
        String getName();
        List<Dependency> getDependencies();
    }

    interface Dependency extends Serializable {
        String getGroupId();
        String getArtifactId();
        String getVersion();
    }

}
