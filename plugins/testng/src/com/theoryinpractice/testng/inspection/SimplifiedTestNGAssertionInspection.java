/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.theoryinpractice.testng.inspection;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SimplifiedTestNGAssertionInspection extends SimplifiableAssertionInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("simplifiable.testng.assertion.display.name");
  }

  @Override
  protected boolean checkTestNG() {
    return true;
  }
}
