/*
 Copyright 2019 Thomas Rosenau

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

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class DiffFoldingTest extends LightCodeInsightFixtureTestCase {

    public void testContext() {
        go();
    }

    public void testContextMulti() {
        go();
    }

    public void testUnified() {
        go();
    }

    public void testUnifiedMulti() {
        go();
    }

    public void testNormal() {
        go();
    }

    public void testNormalMulti() {
        go();
    }

    public void testGit() {
        go("patch");
    }

    public void testGitBinary() {
        go("patch");
    }


    private void go() {
        go("diff");
    }

    private void go(String fileExtension) {
        myFixture.testFoldingWithCollapseStatus(
                getTestDataPath() + "/" + getTestName(false) + ".folded." + fileExtension);
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/diffs";
    }

}
