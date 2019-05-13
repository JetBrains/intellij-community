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
package org.intellij.lang.xpath;

import junit.framework.TestCase;
import org.intellij.lang.xpath.psi.XPath2SequenceType;
import org.intellij.lang.xpath.psi.XPath2Type;
import org.intellij.lang.xpath.psi.XPathType;

public class XPath2StaticTypeTest extends TestCase {

  public void testStatic() {
    assertTrue(XPathType.isAssignable(XPath2Type.NODE, XPath2Type.ITEM));

    assertFalse(XPathType.isAssignable(XPath2Type.NODE, XPath2Type.STRING));
  }

  public void testBooleanAssignability() {
    // via "effective boolean value"
    assertTrue(XPathType.isAssignable(XPath2Type.BOOLEAN, XPath2Type.STRING));
    assertTrue(XPathType.isAssignable(XPath2Type.BOOLEAN, XPath2Type.NUMERIC));
    assertTrue(XPathType.isAssignable(XPath2Type.BOOLEAN, XPath2Type.INTEGER));

    assertFalse(XPathType.isAssignable(XPath2Type.BOOLEAN_STRICT, XPath2Type.STRING));
  }

  public void testAnyAssignability() {
    assertTrue(XPathType.isAssignable(XPath2Type.ITEM, XPath2Type.STRING));
    assertTrue(XPathType.isAssignable(XPath2Type.ITEM, XPath2Type.BOOLEAN));
    assertTrue(XPathType.isAssignable(XPath2Type.ITEM, XPath2Type.DATE));
  }

  public void testNumericAssignability() {
    assertTrue(XPathType.isAssignable(XPath2Type.FLOAT, XPath2Type.INTEGER));
    assertTrue(XPathType.isAssignable(XPath2Type.FLOAT, XPath2Type.DECIMAL));

    assertTrue(XPathType.isAssignable(XPath2Type.DOUBLE, XPath2Type.FLOAT));
    assertTrue(XPathType.isAssignable(XPath2Type.DOUBLE, XPath2Type.INTEGER));
    assertTrue(XPathType.isAssignable(XPath2Type.DOUBLE, XPath2Type.DECIMAL));

    assertFalse(XPathType.isAssignable(XPath2Type.FLOAT, XPath2Type.DOUBLE));
    assertFalse(XPathType.isAssignable(XPath2Type.DECIMAL, XPath2Type.DOUBLE));

    assertFalse(XPathType.isAssignable(XPath2Type.NUMERIC, XPath2Type.BOOLEAN));
  }

  public void testStringAssignability() {
    assertTrue(XPathType.isAssignable(XPath2Type.STRING, XPath2Type.ANYURI));
    assertTrue(XPathType.isAssignable(XPath2Type.STRING, XPath2SequenceType.create(XPath2Type.STRING)));

    assertTrue(XPathType.isAssignable(XPath2SequenceType.create(XPath2Type.STRING), XPath2SequenceType.create(XPath2Type.STRING)));
    assertTrue(XPathType.isAssignable(XPath2SequenceType.create(XPath2Type.STRING), XPath2Type.STRING));

    assertFalse(XPathType.isAssignable(XPath2Type.STRING, XPath2Type.INTEGER));

    assertFalse(XPathType.isAssignable(XPath2SequenceType.create(XPath2Type.STRING), XPath2SequenceType.create(XPath2Type.INTEGER)));
  }
}
