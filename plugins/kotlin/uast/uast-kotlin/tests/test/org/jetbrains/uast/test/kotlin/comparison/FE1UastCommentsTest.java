// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.comparison;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.idea.test.TestRoot;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

@TestRoot("uast/uast-kotlin-fir")
@TestMetadata("uast-kotlin-fir/testData/declaration")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class FE1UastCommentsTest extends AbstractFE1UastCommentsTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
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
