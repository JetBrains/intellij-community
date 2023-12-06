// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader;

import java.io.File;
import java.util.List;

public class KotlinWithJdkAndRuntimeLightProjectDescriptor extends KotlinJdkAndLibraryProjectDescriptor {
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(
        List.of(TestKotlinArtifacts.getKotlinStdlib()),
        List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
      );

    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK8_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(
        List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinStdlibJdk8()),
        List.of(TestKotlinArtifacts.getKotlinStdlibSources(),
                TestKotlinArtifacts.getKotlinStdlibCommonSources(),
                TestKotlinArtifacts.getKotlinStdlibJdk8Sources())
      );
 
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_NO_SOURCES =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(getInstance().getLibraryFiles(), List.of());
 
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_TESTS =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(
        List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinTest()),
        List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
      );
 
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor
      JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_SCRIPT_RUNTIME = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
      List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinScriptRuntime()),
      List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
    );
 
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_REFLECT =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(
        List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinReflect()),
        List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources())
      );
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor FULL_JDK_DESCRIPTOR =
            new KotlinWithJdkAndRuntimeLightProjectDescriptor() {
                @Override
                public Sdk getSdk() {
                    return PluginTestCaseBase.fullJdk();
                }
            };

    public KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        super(
                List.of(TestKotlinArtifacts.getKotlinStdlib()),
                List.of(TestKotlinArtifacts.getKotlinStdlibSources(), TestKotlinArtifacts.getKotlinStdlibCommonSources()),
                null
        );
    }

    public KotlinWithJdkAndRuntimeLightProjectDescriptor(
            @NotNull List<? extends File> libraryFiles,
            @NotNull List<? extends File> librarySourceFiles
    ) {
        super(libraryFiles, librarySourceFiles, null);
    }

    public KotlinWithJdkAndRuntimeLightProjectDescriptor(
            @NotNull List<? extends File> libraryFiles,
            @NotNull List<? extends File> librarySourceFiles,
            @NotNull LanguageLevel languageLevel
    ) {
        super(libraryFiles, librarySourceFiles, languageLevel);
    }

    public static KotlinWithJdkAndRuntimeLightProjectDescriptor getInstance(@NotNull String version) {
        KotlinArtifactsDownloader instance = KotlinArtifactsDownloader.INSTANCE;
        return new KotlinWithJdkAndRuntimeLightProjectDescriptor(
                List.of(instance.downloadArtifactForIdeFromSources("kotlin-stdlib", version)),
                List.of(
                        instance.downloadArtifactForIdeFromSources("kotlin-stdlib", version, "-sources.jar"),
                        instance.downloadArtifactForIdeFromSources("kotlin-stdlib-common", version, "-sources.jar")
                )
        );
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstance() {
        return JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceWithStdlibJdk8() {
        return JDK8_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceNoSources() {
        return JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_NO_SOURCES;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstance(LanguageLevel level) {
        return new KotlinWithJdkAndRuntimeLightProjectDescriptor() {
            @Override
            public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model) {
                super.configureModule(module, model);
                model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
            }
        };
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceWithKotlinTest() {
        return JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_TESTS;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceWithScriptRuntime() {
        return JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_SCRIPT_RUNTIME;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceWithReflect() {
        return JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_REFLECT;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceFullJdk() {
        return FULL_JDK_DESCRIPTOR;
    }
}
