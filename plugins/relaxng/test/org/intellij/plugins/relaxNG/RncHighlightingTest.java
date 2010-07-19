/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import org.intellij.plugins.testUtil.CopyFile;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 25.07.2007
 */
public class RncHighlightingTest extends HighlightingTestBase {

  public String getTestDataPath() {
    return "highlighting/rnc";
  }

  public void testRef3() throws Throwable {
    doHighlightingTest("ref-3.rnc");
  }

  public void testUndefinedRef() throws Throwable {
    doHighlightingTest("undefined-ref.rnc");
  }

  public void testCreateDefintion1() throws Throwable {
    doTestQuickFix("create-definition-1", "rnc");
  }

  public void testCreateDefintion2() throws Throwable {
    doTestQuickFix("create-definition-2", "rnc");
  }

  public void testNsPrefix() throws Throwable {
    doHighlightingTest("ns-prefix.rnc");
  }

  public void testNsPrefixKeyword() throws Throwable {
    doHighlightingTest("ns-prefix-keyword.rnc");
  }

  public void testUnresolvedNsPrefix1() throws Throwable {
    doHighlightingTest("unresolved-ns-prefix-1.rnc");
  }

  public void testUnresolvedNsPrefix2() throws Throwable {
    doHighlightingTest("unresolved-ns-prefix-2.rnc");
  }

  public void testUnresolvedNsPrefix3() throws Throwable {
    doHighlightingTest("unresolved-ns-prefix-3.rnc");
  }

  public void testCreateNsPrefix() throws Throwable {
    doTestQuickFix("create-ns-prefix-1", "rnc");
  }

  public void testDatatypePrefix() throws Throwable {
    doHighlightingTest("datatype-prefix.rnc");
  }

  public void testUnresolvedDatatypePrefix1() throws Throwable {
    doHighlightingTest("unresolved-datatype-prefix-1.rnc");
  }

  public void testUnresolvedDatatypePrefix2() throws Throwable {
    doHighlightingTest("unresolved-datatype-prefix-2.rnc");
  }

  public void testCreateDatatypesPrefix() throws Throwable {
    doTestQuickFix("create-datatypes-prefix-1", "rnc");
  }

  public void testUnresolvedInclude() throws Throwable {
    doHighlightingTest("unresolved-include.rnc");
  }

  public void testParentRef() throws Throwable {
    doHighlightingTest("parent-ref.rnc");
  }

  public void testBadParentRef1() throws Throwable {
    doHighlightingTest("bad-parent-ref-1.rnc");
  }

  public void testAnnotation() throws Throwable {
    doHighlightingTest("annotation.rnc");
  }

  @CopyFile("fo/*.rnc")
  public void testFoMain() throws Throwable {
    doHighlightingTest("fo/main.rnc");
  }

  @CopyFile("fo/*.rnc")
  public void testFoElements() throws Throwable {
    doHighlightingTest("fo/elements.rnc");
  }

  public void testFoDatatype() throws Throwable {
    doHighlightingTest("fo/datatype.rnc");
  }

  public void testRngSchema() throws Throwable {
    doHighlightingTest("rng-schema.rnc");
  }

  public void testDocbook() throws Throwable {
    doHighlightingTest("docbook.rnc");
  }
}