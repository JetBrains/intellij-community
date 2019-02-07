// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.siyeh.ig.testFrameworks.BaseAssertEqualsBetweenInconvertibleTypesInspection;

public class AssertEqualsBetweenInconvertibleTypesTestNGInspection extends BaseAssertEqualsBetweenInconvertibleTypesInspection {
  @Override
  protected boolean checkTestNG() {
    return true;
  }
}