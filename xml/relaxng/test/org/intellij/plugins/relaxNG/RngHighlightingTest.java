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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.xml.XmlTag;

public class RngHighlightingTest extends HighlightingTestBase {

  @Override
  public String getTestDataPath() {
    return "highlighting/rng";
  }

  public void testSimpleSchema() {
    doHighlightingTest("simple.rng");
  }

  public void testRef1() {
    doHighlightingTest("ref-1.rng");
  }

  public void testRef2() {
    doHighlightingTest("ref-2.rng");
  }

  public void testRef3() {
    doHighlightingTest("ref-3.rng");
  }

  public void testBadRef() {
    doHighlightingTest("bad-ref.rng");
  }

  public void testBadRef2() {
    doHighlightingTest("bad-ref-2.rng");
  }

  public void testBadRef3() {
    doHighlightingTest("bad-ref-3.rng");
  }

  public void testBadRef4() {
    doHighlightingTest("bad-ref-4.rng");
  }

  public void testCreateDefinition1() {
    doTestQuickFix("create-definition-1", "rng");
  }

  public void testParentRef1() {
    doHighlightingTest("parent-ref-1.rng");
  }

  public void testParentRef2() {
    myTestFixture.copyFileToProject("include.rng");
    doHighlightingTest("parent-ref-2.rng");
  }

  public void testBadParentRef1() {
    doHighlightingTest("bad-parent-ref-1.rng");
  }

  public void testBadParentRef2() {
    myTestFixture.copyFileToProject("bad-parent-ref-1.rng");
    doHighlightingTest("bad-parent-ref-2.rng");
  }

  public void testCreateDefinition2() {
    doTestQuickFix("create-definition-2", "rng");
  }

  public void testMissingStartElement() {
    doCustomHighlighting("missing-start-element.rng", false, true);
  }

  public void testMissingStartElementAndInclude() {
    myTestFixture.copyFileToProject("included-grammar.rng");
    doCustomHighlighting("missing-start-element-and-include.rng", false, true);
  }

  public void testBadNsPrefix() {
    doHighlightingTest("bad-ns-prefix.rng");
  }

  public void testBadElement() {
    doExternalToolHighlighting("bad-element.rng");
  }

  public void testInclude() {
    myTestFixture.copyFileToProject("include.rng");
    doHighlightingTest("good-include.rng");
  }

  public void testIncludedRef1() {
    myTestFixture.copyFileToProject("include.rng");
    doHighlightingTest("good-include-ref-1.rng");
  }

  public void testResolveIncludedRef1() {
    myTestFixture.copyFileToProject("include.rng");

    final PsiReference ref = myTestFixture.getReferenceAtCaretPositionWithAssertion("resolve-include-ref-1.rng");
    final PsiElement element = ref.resolve();
    assertTrue(element instanceof XmlTag);
    assertNotSame(element.getContainingFile(), ref.getElement().getContainingFile());
    assertEquals(0, ((XmlTag)element).getSubTags().length);
  }

  public void testResolveIncludedRef2() {
    myTestFixture.copyFileToProject("include.rng");

    final PsiReference ref = myTestFixture.getReferenceAtCaretPositionWithAssertion("resolve-include-ref-2.rng");
    assertTrue("PolyVariantRef", ref instanceof PsiPolyVariantReference);

    final PsiElement element = ref.resolve();
    assertNull(element);

    final ResolveResult[] results = ((PsiPolyVariantReference)ref).multiResolve(false);
    assertEquals(2, results.length);

    for (ResolveResult result : results) {
      PsiElement e = result.getElement();
      assertTrue(e instanceof XmlTag);

      final int contentLength = ((XmlTag)e).getSubTags().length;
      if (e.getContainingFile() == ref.getElement().getContainingFile()) {
        assertEquals(1, contentLength);
      } else {
        assertEquals(0, contentLength);
      }
    }
  }

  public void testBadInclude() {
    doHighlightingTest("bad-include.rng");
  }
}