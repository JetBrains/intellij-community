/*
 Copyright 2020 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class DiffFoldingTest extends LightJavaCodeInsightFixtureTestCase {

    public void testContext() {
        doTest();
    }

    public void testContextMulti() {
        doTest();
    }

    public void testUnified() {
        doTest();
    }

    public void testUnifiedMulti() {
        doTest();
    }

    public void testNormal() {
        doTest();
    }

    public void testNormalMulti() {
        doTest();
    }

    public void testGit() {
        doTest("patch");
    }

    public void testGitBinary() {
        doTest("patch");
    }


    private void doTest() {
        doTest("diff");
    }

    private void doTest(String fileExtension) {
        myFixture.testFoldingWithCollapseStatus(
                getTestDataPath() + "/" + getTestName(false) + ".folded." + fileExtension);
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/diffs";
    }

}
