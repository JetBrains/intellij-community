// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;

import static com.intellij.testFramework.RunAll.runAll;

abstract public class KotlinDaemonAnalyzerTestCase extends DaemonAnalyzerTestCase
        implements ExpectedPluginModeProvider {

    private Ref<Disposable> vfsDisposable;

    @Override
    protected void setUp() throws Exception {
        ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, super::setUp);
        vfsDisposable = KotlinTestUtils.allowProjectRootAccess(this);
    }

    @Override
    protected void tearDown() {
        runAll(() -> KotlinTestUtils.disposeVfsRootAccess(vfsDisposable),
               () -> super.tearDown());
    }
}
