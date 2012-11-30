/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.06.2008
*/
public class Xslt2CompletionTest extends TestBase {

    public void testCustomFunctions() throws Throwable {
        doXsltCompletion();
    }

    public void testTypeCompletion() throws Throwable {
        doXsltCompletion();
    }

    public void testSchemaTypeCompletion() throws Throwable {
      final String name = getTestFileName();
      myFixture.testCompletionTyping(name + ".xsl", "\n", name + "_after.xsl");
    }

    private void doXsltCompletion(String... moreFiles) throws Throwable {
        final String name = getTestFileName();
        myFixture.testCompletion(name + ".xsl", name + "_after.xsl", moreFiles);
    }

    @Override
    protected String getSubPath() {
        return "xslt2/completion";
    }
}