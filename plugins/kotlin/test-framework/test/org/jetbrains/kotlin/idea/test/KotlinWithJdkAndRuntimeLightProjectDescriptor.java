// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifactsKt;

import java.io.File;
import java.util.List;

public class KotlinWithJdkAndRuntimeLightProjectDescriptor extends KotlinJdkAndLibraryProjectDescriptor {
    protected KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        super(List.of(TestKotlinArtifacts.getKotlinStdlib()),
              List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources()));
    }

    public KotlinWithJdkAndRuntimeLightProjectDescriptor(
            @NotNull List<? extends File> libraryFiles,
            @NotNull List<? extends File> librarySourceFiles
    ) {
        super(libraryFiles, librarySourceFiles);
    }

    @NotNull
    public static KotlinWithJdkAndRuntimeLightProjectDescriptor getInstance(@NotNull String version) {
        return new KotlinWithJdkAndRuntimeLightProjectDescriptor(
                List.of(TestKotlinArtifactsKt.downloadOrReportUnavailability("kotlin-stdlib", version)),
                List.of(
                        TestKotlinArtifactsKt.downloadOrReportUnavailability("kotlin-stdlib", version, "-sources.jar"),
                        TestKotlinArtifactsKt.downloadOrReportUnavailability("kotlin-stdlib-common", version, "-sources.jar")
                )
        );
    }

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            List.of(TestKotlinArtifacts.getKotlinStdlib()),
            List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_WITH_STDLIB_JDK8 = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinStdlibJdk8()),
            List.of(TestKotlinArtifacts.getKotlinStdlibSources(),
                    TestKotlinArtifacts.getKotlinStdlibCommonSources(),
                    TestKotlinArtifacts.getKotlinStdlibJdk8Sources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_NO_SOURCES = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            INSTANCE.getLibraryFiles(), List.of()
    );

    public static KotlinWithJdkAndRuntimeLightProjectDescriptor getInstance(LanguageLevel level) {
        return new KotlinWithJdkAndRuntimeLightProjectDescriptor() {
            @Override
            public void configureModule(
                    @NotNull Module module, @NotNull ModifiableRootModel model
            ) {
                super.configureModule(module, model);
                model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
            }
        };
    }

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_WITH_KOTLIN_TEST = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinTest()),
            List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_WITH_SCRIPT_RUNTIME = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinScriptRuntime()),
            List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_WITH_REFLECT = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinReflect()),
            List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_FULL_JDK = new KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        @Override
        public Sdk getSdk() {
            return PluginTestCaseBase.fullJdk();
        }
    };
}
