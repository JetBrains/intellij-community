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
package org.intellij.lang.xpath.xslt;

import org.intellij.lang.xpath.TestBase;

public class XsltCompletionTest extends TestBase {

    public void testLocalVariable() {
        doXsltCompletion();
    }

    public void testGlobalVariable() {
        doXsltCompletion();
    }

    public void testTemplates() {
        doXsltCompletion();
    }

    public void testModes() {
        doXsltCompletion();
    }

    public void testNamedTemplateParams() {
        doXsltCompletion();
    }

    public void testIncludedTemplateParam() {
        doXsltCompletion("included.xsl");
    }

    public void testApplyTemplateParams() {
        doXsltCompletion();
    }

    public void testIncludedVariable() {
        doXsltCompletion("included.xsl");
    }

    public void testUsedNames() {
        doXsltCompletion();
    }

    private void doXsltCompletion(String... moreFiles) {
        final String name = getTestFileName();
        myFixture.testCompletion(name + ".xsl", name + "_after.xsl", moreFiles);
    }

    @Override
    protected String getSubPath() {
        return "xslt/completion";
    }
}