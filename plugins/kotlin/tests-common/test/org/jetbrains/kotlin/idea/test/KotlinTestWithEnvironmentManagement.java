// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase;

public abstract class KotlinTestWithEnvironmentManagement extends KtUsefulTestCase {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    @NotNull
    protected KotlinCoreEnvironment createEnvironmentWithMockJdk(@NotNull ConfigurationKind configurationKind) {
        return createEnvironmentWithJdk(configurationKind, TestJdkKind.MOCK_JDK);
    }

    @NotNull
    protected KotlinCoreEnvironment createEnvironmentWithJdk(@NotNull ConfigurationKind configurationKind, @NotNull TestJdkKind jdkKind) {
        return KotlinTestUtils.createEnvironmentWithJdkAndNullabilityAnnotationsFromIdea(getTestRootDisposable(), configurationKind, jdkKind);
    }
}
