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

import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyAugmentAssignmentInspection;

/**
 * User: ktisha
 */
public class PyAugmentAssignmentQuickFixTest extends PyQuickFixTestCase {

  public void testSimple() {  // PY-1415
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testWithContext() {  // PY-2481
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testPercent() {  // PY-3197
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testDivision() {  // PY-5037
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testReferences() {  // PY-6331
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testFunction() {  // PY-6678
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testSubscription() {  // PY-7715
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }
}
