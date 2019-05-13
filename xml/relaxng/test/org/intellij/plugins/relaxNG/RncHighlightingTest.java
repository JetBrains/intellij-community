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

public class RncHighlightingTest extends HighlightingTestBase {

  @Override
  public String getTestDataPath() {
    return "highlighting/rnc";
  }

  public void testRef3() {
    doHighlightingTest("ref-3.rnc");
  }

  public void testUndefinedRef() {
    doHighlightingTest("undefined-ref.rnc");
  }

  public void testCreateDefintion1() {
    doTestQuickFix("create-definition-1", "rnc");
  }

  public void testCreateDefintion2() {
    doTestQuickFix("create-definition-2", "rnc");
  }

  public void testNsPrefix() {
    doHighlightingTest("ns-prefix.rnc");
  }

  public void testNsPrefixKeyword() {
    doHighlightingTest("ns-prefix-keyword.rnc");
  }

  public void testUnresolvedNsPrefix1() {
    doHighlightingTest("unresolved-ns-prefix-1.rnc");
  }

  public void testUnresolvedNsPrefix2() {
    doHighlightingTest("unresolved-ns-prefix-2.rnc");
  }

  public void testUnresolvedNsPrefix3() {
    doHighlightingTest("unresolved-ns-prefix-3.rnc");
  }

  public void testCreateNsPrefix() {
    doTestQuickFix("create-ns-prefix-1", "rnc");
  }

  public void testDatatypePrefix() {
    doHighlightingTest("datatype-prefix.rnc");
  }

  public void testUnresolvedDatatypePrefix1() {
    doHighlightingTest("unresolved-datatype-prefix-1.rnc");
  }

  public void testUnresolvedDatatypePrefix2() {
    doHighlightingTest("unresolved-datatype-prefix-2.rnc");
  }

  public void testCreateDatatypesPrefix() {
    doTestQuickFix("create-datatypes-prefix-1", "rnc");
  }

  public void testUnresolvedInclude() {
    doHighlightingTest("unresolved-include.rnc");
  }

  public void testUnresolvedExternal() {
    doHighlightingTest("unresolved-external.rnc");
  }

  public void testParentRef() {
    doHighlightingTest("parent-ref.rnc");
  }

  public void testBadParentRef1() {
    doHighlightingTest("bad-parent-ref-1.rnc");
  }

  public void testAnnotation() {
    doHighlightingTest("annotation.rnc");
  }

  @CopyFile("fo/*.rnc")
  public void testFoMain() {
    doHighlightingTest("fo/main.rnc");
  }

  @CopyFile("fo/*.rnc")
  public void testFoElements() {
    doHighlightingTest("fo/elements.rnc");
  }

  public void testFoDatatype() {
    doHighlightingTest("fo/datatype.rnc");
  }

  public void testRngSchema() {
    doHighlightingTest("rng-schema.rnc");
  }

  public void testDocbook() {
    doHighlightingTest("docbook.rnc");
  }
}