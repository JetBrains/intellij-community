// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test;

public class JUnit3RunnerWithInners extends org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners {
    public JUnit3RunnerWithInners(Class<?> testClass) {
        super(testClass);
    }
}
