/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import com.jetbrains.python.psi.LanguageLevel;

@TestDataPath("$CONTENT_ROOT/../testData//quickFixes/AddFieldQuickFixTest/")
public class PyAddFieldQuickFixTest extends PyQuickFixTestCase {

  public void testAddClassField() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.field.to.class", "FIELD", "A"));
  }

  public void testAddFieldFromMethod() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.field.to.class", "y", "A"));
  }

  public void testAddFieldFromInstance() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.field.to.class", "y", "A"));
  }

  public void testAddFieldAddConstructor() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class,
                   PyPsiBundle.message("QFIX.add.field.to.class", "x", "B"),
                   LanguageLevel.PYTHON27);
  }

  public void testAddFieldNewConstructor() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.field.to.class", "x", "B"));
  }

  public void testFromUnusedParameter() {  // PY-1398
    doQuickFixTest(PyUnusedLocalInspection.class, "Add field 'foo' to class A");
  }

  // PY-14733
  public void testAddFieldInitializationInsideEmptyInit() {
    doQuickFixTest(PyUnusedLocalInspection.class, "Add field 'foo' to class A");
  }

  public void testFromUnusedParameterKeyword() {  // PY-1602
    doQuickFixTest(PyUnusedLocalInspection.class, "Add field 'foo' to class A");
  }

  // PY-21284
  public void testAddFieldAddConstructorWithTypeAnnotation() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, "Add field 'param' to class DerivedClass");
  }
}
