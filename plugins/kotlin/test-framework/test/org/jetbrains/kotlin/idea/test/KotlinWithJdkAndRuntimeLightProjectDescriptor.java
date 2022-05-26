// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KotlinWithJdkAndRuntimeLightProjectDescriptor extends KotlinJdkAndLibraryProjectDescriptor {
    protected KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        super(Collections.singletonList(KotlinArtifacts.getKotlinStdlib()),
              Collections.singletonList(KotlinArtifacts.getKotlinStdlibSources()));
    }

    public KotlinWithJdkAndRuntimeLightProjectDescriptor(
            @NotNull List<? extends File> libraryFiles,
            @NotNull List<? extends File> librarySourceFiles
    ) {
        super(libraryFiles, librarySourceFiles);
    }

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            Arrays.asList(KotlinArtifacts.getKotlinStdlib()),
            Collections.singletonList(KotlinArtifacts.getKotlinStdlibSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_WITH_STDLIB_JDK8 = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            List.of(KotlinArtifacts.getKotlinStdlib(), KotlinArtifacts.getKotlinStdlibJdk8()),
            List.of(KotlinArtifacts.getKotlinStdlibSources(), KotlinArtifacts.getKotlinStdlibJdk8Sources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_NO_SOURCES = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            INSTANCE.getLibraryFiles(), Collections.emptyList()
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
            Arrays.asList(KotlinArtifacts.getKotlinStdlib(),
                          KotlinArtifacts.getKotlinTest()),
            Collections.singletonList(KotlinArtifacts.getKotlinStdlibSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_WITH_SCRIPT_RUNTIME = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            Arrays.asList(KotlinArtifacts.getKotlinStdlib(),
                          KotlinArtifacts.getKotlinScriptRuntime()),
            Collections.singletonList(KotlinArtifacts.getKotlinStdlibSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_WITH_REFLECT = new KotlinWithJdkAndRuntimeLightProjectDescriptor(
            Arrays.asList(KotlinArtifacts.getKotlinStdlib(),
                          KotlinArtifacts.getKotlinReflect()),
            Collections.singletonList(KotlinArtifacts.getKotlinStdlibSources())
    );

    @NotNull
    public static final KotlinWithJdkAndRuntimeLightProjectDescriptor INSTANCE_FULL_JDK = new KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        @Override
        public Sdk getSdk() {
            return PluginTestCaseBase.fullJdk();
        }
    };
}
