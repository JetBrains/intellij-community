// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;

import java.io.File;

import static com.intellij.testFramework.RunAll.runAll;

/**
 * Please use KotlinLightCodeInsightFixtureTestCase as the base class for all new tests.
 */
@Deprecated
public abstract class KotlinCodeInsightTestCase extends JavaCodeInsightTestCase {
    private Ref<Disposable> vfsDisposable;

    @Override
    final protected @NotNull String getTestDataPath() {
        return KotlinTestUtils.toSlashEndingDirPath(getTestDataDirectory().getAbsolutePath());
    }

    protected @NotNull File getTestDataDirectory() {
        return new File(super.getTestDataPath());
    }

    @Override
    protected void setUp() throws Exception {
        vfsDisposable = KotlinTestUtils.allowProjectRootAccess(this);
        super.setUp();
    }

    @Override
    protected void tearDown() {
        runAll(
                () -> super.tearDown(),
                () -> KotlinTestUtils.disposeVfsRootAccess(vfsDisposable)
        );
    }
}
