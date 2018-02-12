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

import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.validation.inspections.*;

public class XPathHighlightingTest extends TestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
      new XPathSupportLoader();
      //noinspection unchecked
      myFixture.enableInspections(new Class[]{
        CheckNodeTest.class,
        ImplicitTypeConversion.class,
        RedundantTypeConversion.class,
        IndexZeroPredicate.class,
        HardwiredNamespacePrefix.class,
      });
    }

    public void testPathTypeMismatch() {
        doXPathHighlighting();
    }

    public void testUnknownFunction() {
        doXPathHighlighting();
    }

    public void testMissingArgument() {
        doXPathHighlighting();
    }

    public void testInvalidArgument() {
        doXPathHighlighting();
    }

    public void testIndexZero() {
        doXPathHighlighting();
    }

    public void testSillyStep() {
        doXPathHighlighting();
    }

    public void testNonSillyStepIDEADEV33539() {
        doXPathHighlighting();
    }

    public void testHardwiredPrefix() {
        doXPathHighlighting();
    }

    public void testNumberFollowedByToken() {
      doXPathHighlighting();
    }

    public void testScientificNotationNumber() {
      doXPathHighlighting();
    }

    public void testMalformedStringLiteral() {
      doXPathHighlighting();
    }

    public void testMalformedStringLiteral2() {
      doXPathHighlighting();
    }

    public void testMalformedEmptyStringLiteral() {
      doXPathHighlighting();
    }

    public void testQuotedStringLiteral() {
      doXPathHighlighting();
    }

    // IDEA-67413
    public void testUnionSubExpression() {
      doXPathHighlighting();
    }

    // IDEA-102422
    public void testPrefixedNameAnd() {
      doXPathHighlighting();
    }

    private void doXPathHighlighting(String... moreFiles) {
        final String name = getTestFileName();
        myFixture.testHighlighting(true, false, false, ArrayUtil.append(moreFiles, name + ".xpath"));
    }

    @Override
    protected String getSubPath() {
        return "xpath/highlighting";
    }
}
