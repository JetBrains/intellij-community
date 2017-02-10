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

import com.siyeh.ig.testFrameworks.AssertsWithoutMessagesInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Bas Leijdekkers
 */
public class AssertsWithoutMessagesTestNGInspection extends AssertsWithoutMessagesInspection {
  @NonNls private static final Map<String, Integer> ourAssertMethods = new HashMap<>(10);

  static {
    ourAssertMethods.put("assertArrayEquals", 2);
    ourAssertMethods.put("assertEquals", 2);
    ourAssertMethods.put("assertEqualsNoOrder", 2);
    ourAssertMethods.put("assertFalse", 1);
    ourAssertMethods.put("assertNotEquals", 2);
    ourAssertMethods.put("assertNotNull", 1);
    ourAssertMethods.put("assertNotSame", 2);
    ourAssertMethods.put("assertNull", 1);
    ourAssertMethods.put("assertSame", 2);
    ourAssertMethods.put("assertTrue", 1);
    ourAssertMethods.put("fail", 0);
  }

  @Override
  protected Map<String, Integer> getAssertMethods() {
    return ourAssertMethods;
  }

  @Override
  protected boolean checkTestNG() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Message missing on TestNG assertion";
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return "TestNG <code>#ref()</code> without message #loc";
  }
}