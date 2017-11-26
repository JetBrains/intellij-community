/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools;

import com.android.testutils.TestUtils;
import com.android.tools.perflogger.BenchmarkLogger;
import com.android.tools.perflogger.BenchmarkLogger.*;
import org.junit.Test;

import java.io.File;
import java.util.*;

public class AndroidStudioBundleTest {
  @Test
  public void testLoggingBinarySize() throws Exception {
    List<String> files = Arrays.asList("android-studio.win.zip",
                                       "android-studio.win32.zip",
                                       "android-studio.mac.zip",
                                       "android-studio.tar.gz");

    Benchmark benchmark = new Benchmark("Android Studio Binary Size");
    for (String file : files) {
      // getWorkspaceFile asserts the file exists.
      File binary = TestUtils.getWorkspaceFile("tools/idea/" + file);

      BenchmarkLogger.log(benchmark, file, binary.length());
    }

    // TODO handle failure cases
  }
}
