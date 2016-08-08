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
package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author vlan
 */
public class PyStdlibUtil {
  @Nullable private static Set<String> PACKAGES = loadStdlibPackagesList();

  private PyStdlibUtil() {
  }

  @Nullable
  public static Collection<String> getPackages() {
    return PACKAGES;
  }

  @Nullable
  private static Set<String> loadStdlibPackagesList() {
    final Logger log = Logger.getInstance(PyStdlibUtil.class.getName());
    final String helperPath = PythonHelpersLocator.getHelperPath("/tools/stdlib_packages.txt");
    try {
      final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(helperPath)));
      try {
        final Set<String> result = new HashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
          result.add(line);
        }
        return result;
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      log.error("Cannot read list of standard library packages: " + e.getMessage());
    }
    return null;
  }
}
