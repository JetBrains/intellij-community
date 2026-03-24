// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.debugger.test;

import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.TestDataPath;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.debugger.test.AbstractKotlinSteppingPacketsNumberTest;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

@SuppressWarnings("all")
@PerformanceUnitTest
@TestRoot("jvm-debugger/test/k2")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("../testData/stepping/packets")
public class K2IdeK2CodeKotlinSteppingPacketsNumberTest extends AbstractKotlinSteppingPacketsNumberTest {
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public final KotlinPluginMode getPluginMode() {
        return KotlinPluginMode.K2;
    }

    @Override
    public JvmClosureGenerationScheme lambdasGenerationScheme() {
        return JvmClosureGenerationScheme.INDY;
    }

    @Override
    public boolean getCompileWithK2() {
        return true;
    }

    private void runTest(String testDataFilePath) throws Exception {
        runBenchmark(new Function0<>() {
            @Override
            public Unit invoke() {
                try {
                    KotlinTestUtils.runTest(K2IdeK2CodeKotlinSteppingPacketsNumberTest.this::doCustomTest,
                                            K2IdeK2CodeKotlinSteppingPacketsNumberTest.this,
                                            testDataFilePath);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return Unit.INSTANCE;
            }
        });
    }

    @TestMetadata("coroutineStepping.kt")
    public void testCoroutineStepping() throws Exception {
        runTest("../testData/stepping/packets/coroutineStepping.kt");
    }

    @TestMetadata("evaluatableGetters.kt")
    public void testEvaluatableGetters() throws Exception {
        runTest("../testData/stepping/packets/evaluatableGetters.kt");
    }

    @TestMetadata("veryLongCoroutineStack.kt")
    public void testVeryLongCoroutineStack() throws Exception {
        runTest("../testData/stepping/packets/veryLongCoroutineStack.kt");
    }
}
