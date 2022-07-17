// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;

public abstract class KotlinTestWithEnvironment extends KotlinTestWithEnvironmentManagement {
    private KotlinCoreEnvironment environment;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        environment = createEnvironment();
    }

    @Override
    protected void tearDown() throws Exception {
        environment = null;
        super.tearDown();
    }

    protected abstract KotlinCoreEnvironment createEnvironment() throws Exception;

    @NotNull
    public KotlinCoreEnvironment getEnvironment() {
        return environment;
    }

    @NotNull
    public Project getProject() {
        return getEnvironment().getProject();
    }
}
