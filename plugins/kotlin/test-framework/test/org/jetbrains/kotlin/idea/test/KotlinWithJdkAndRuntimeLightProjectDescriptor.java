// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts;

import java.io.File;
import java.util.List;

public class KotlinWithJdkAndRuntimeLightProjectDescriptor extends KotlinJdkAndLibraryProjectDescriptor {

    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR =
            new KotlinWithJdkAndRuntimeLightProjectDescriptor();

    private static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK8_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(
        List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinStdlibJdk8()),
        List.of(TestKotlinArtifacts.getKotlinStdlibSources(),
                TestKotlinArtifacts.getKotlinStdlibJdk8Sources())
      );
 
    private static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_NO_SOURCES =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(getInstance().getLibraryFiles(), List.of());
 
    private static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_TESTS =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(
        List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinTest()),
        List.of(TestKotlinArtifacts.getKotlinStdlibSources())
      );
 
    private static final KotlinWithJdkAndRuntimeLightProjectDescriptor
      JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_SCRIPT_RUNTIME = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
      List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinScriptRuntime()),
      List.of(TestKotlinArtifacts.getKotlinStdlibSources())
    );
 
    private static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_WITH_REFLECT =
      new KotlinWithJdkAndRuntimeLightProjectDescriptor(
        List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinReflect()),
        List.of(TestKotlinArtifacts.getKotlinStdlibSources())
      );

    private static final KotlinWithJdkAndRuntimeLightProjectDescriptor FULL_JDK_DESCRIPTOR =
            new KotlinWithJdkAndRuntimeLightProjectDescriptor() {
                @Override
                public Sdk getSdk() {
                    return PluginTestCaseBase.fullJdk();
                }
            };

    public KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        this(List.of(TestKotlinArtifacts.getKotlinStdlib()),
             List.of(TestKotlinArtifacts.getKotlinStdlibSources()));
    }

    public KotlinWithJdkAndRuntimeLightProjectDescriptor(
            @NotNull List<? extends File> libraryFiles,
            @NotNull List<? extends File> librarySourceFiles
    ) {
        this(libraryFiles, librarySourceFiles, null);
    }

    public KotlinWithJdkAndRuntimeLightProjectDescriptor(
            @NotNull List<? extends File> libraryFiles,
            @NotNull List<? extends File> librarySourceFiles,
            @Nullable LanguageLevel languageLevel
    ) {
        super(libraryFiles, librarySourceFiles, languageLevel);
    }

    public static KotlinWithJdkAndRuntimeLightProjectDescriptor getInstance(@NotNull String version) {
        if (!version.equals("1.7.0")) {
            throw new RuntimeException("Only version 1.7.0 is supported for now");
        }
        return new KotlinWithJdkAndRuntimeLightProjectDescriptor(
                List.of(TestKotlinArtifacts.getKotlinStdlib170()),
                List.of(TestKotlinArtifacts.getKotlinStdlib170Sources(), TestKotlinArtifacts.getKotlinStdlibCommon170Sources())
        );
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstance() {
        return JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceWithStdlibJdk8() {
        return JDK8_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR;
    }
    private static final KotlinWithJdkAndRuntimeLightProjectDescriptor JDK10_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR =
            new KotlinWithJdkAndRuntimeLightProjectDescriptor(
                    List.of(TestKotlinArtifacts.getKotlinStdlib(), TestKotlinArtifacts.getKotlinStdlibJdk8()),
                    List.of(TestKotlinArtifacts.getKotlinStdlibSources(),
                            TestKotlinArtifacts.getKotlinStdlibJdk8Sources()),
                    LanguageLevel.JDK_10
            );
    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceWithStdlibJdk10() {
        return JDK10_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR;
    }

    public static @NotNull KotlinWithJdkAndRuntimeLightProjectDescriptor getInstanceNoSources() {
        return JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR_NO_SOURCES;
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
