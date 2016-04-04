/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.edu;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

@NonNls
public class EduNames {
  public static final String TASK_HTML = "task.html";
  public static final String HINTS = "hints";
  public static final String LESSON = "lesson";
  public static final String LESSON_TITLED = StringUtil.toTitleCase(LESSON);
  public static final String TASK = "task";
  public static final String TASK_TITLED = StringUtil.toTitleCase(TASK);
  public static final String COURSE = "course";
  public static final String TEST_TAB_NAME = "test";
  public static final String USER_TEST_INPUT = "input";
  public static final String USER_TEST_OUTPUT = "output";
  public static final String WINDOW_POSTFIX = "_window.";
  public static final String WINDOWS_POSTFIX = "_windows";
  public static final String USER_TESTS = "userTests";
  public static final String TESTS_FILE = "tests.py";
  public static final String TEST_HELPER = "test_helper.py";

  public static final String SANDBOX_DIR = "Sandbox";
  public static final String COURSE_META_FILE = "course.json";
  public static String PYCHARM_ADDITIONAL = "PyCharm additional materials";

  private EduNames() {
  }

}
