// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

@TestRoot("uast/uast-kotlin-fir/tests")
@TestMetadata("uast-kotlin-fir/tests/testData/declaration")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class FirUastCommentsTest extends AbstractFirUastCommentsTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("commentsOnDataClass.kt")
    public void testCommentsOnDataClass() throws Exception {
        runTest("testData/declaration/commentsOnDataClass.kt");
    }

    @TestMetadata("commentsOnProperties.kt")
    public void testCommentsOnProperties() throws Exception {
        runTest("testData/declaration/commentsOnProperties.kt");
    }

    @TestMetadata("facade.kt")
    public void testFacade() throws Exception {
        runTest("testData/declaration/facade.kt");
    }

    @TestMetadata("objects.kt")
    public void testObjects() throws Exception {
        runTest("testData/declaration/objects.kt");
    }
}
