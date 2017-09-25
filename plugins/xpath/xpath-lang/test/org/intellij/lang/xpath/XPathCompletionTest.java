/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath;

public class XPathCompletionTest extends TestBase {

  public void testAxis() {
        doXPathCompletion("ancestor", "ancestor-or-self", "attribute");
    }

    public void testAxisInsert() {
        doXPathCompletion();
    }

    public void testPartialAxis() {
        doXPathCompletion("ancestor", "ancestor-or-self");
    }

    public void testFunctions() {
        doXPathCompletion("text()", "translate(string, string, string)", "true()");
    }

    public void testFunctionInsert1() {
        doXPathCompletion();
    }

    public void testFunctionInsert2() {
        doXPathCompletion();
    }

    private void doXPathCompletion() {
        final String name = getTestFileName();
        myFixture.configureByFile(name + ".xpath");
        if (myFixture.completeBasic() != null) {
          myFixture.type('\n');
        }
        myFixture.checkResultByFile(name + "_after.xpath");
    }

    private void doXPathCompletion(String... expectedVariants) {
        myFixture.configureByFile(getTestFileName() + ".xpath");
        myFixture.completeBasic();
        myFixture.assertPreferredCompletionItems(0, expectedVariants);
    }

    @Override
    protected String getSubPath() {
        return "xpath/completion";
    }
}