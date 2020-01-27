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
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.WindowDeviationAnalyzer;
import com.android.tools.perflogger.Metric;
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

    Benchmark benchmark =
      new Benchmark.Builder("Android Studio Binary Size").build();
    for (String file : files) {
      // getWorkspaceFile asserts the file exists.
      File binary = TestUtils.getWorkspaceFile("tools/idea/" + file);

      benchmark.log(
        file,
        binary.length(),
        // we don't expect this to deviate so tighten parameters
        // to detect slightest regression.
        new WindowDeviationAnalyzer.Builder()
          .setRunInfoQueryLimit(5)
          .setRecentWindowSize(1)
          .addMeanTolerance(
            new WindowDeviationAnalyzer.MeanToleranceParams.Builder()
              .setMeanCoeff(0.01)
              .setStddevCoeff(0.0)
              .build())
          .build());
    }

    // TODO handle failure cases
  }
}
