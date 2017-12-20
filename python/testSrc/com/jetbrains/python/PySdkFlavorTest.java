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
package com.jetbrains.python;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PyPySdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PySdkFlavorTest extends PyTestCase {
  public void testPython27VersionString() {
    final PythonSdkFlavor flavor = UnixPythonSdkFlavor.INSTANCE;
    final String versionOutput = "Python 2.7.6\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("Python 2.7.6", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON27, flavor.getLanguageLevel(mockSdk));
  }

  public void testPython34VersionString() {
    final PythonSdkFlavor flavor = UnixPythonSdkFlavor.INSTANCE;
    final String versionOutput = "Python 3.4.0\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("Python 3.4.0", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON34, flavor.getLanguageLevel(mockSdk));
  }

  public void testJythonVersionString() {
    final PythonSdkFlavor flavor = JythonSdkFlavor.INSTANCE;
    final String versionOutput = "Jython 2.6.3\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("Jython 2.6.3", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON26, flavor.getLanguageLevel(mockSdk));
  }

  public void testJythonWithWarningsVersionString() {
    final PythonSdkFlavor flavor = JythonSdkFlavor.INSTANCE;
    final String versionOutput = "\"my\" variable $jythonHome masks earlier declaration in same scope at /usr/bin/jython line 15.\n" +
                                 "Jython 2.6.3\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("Jython 2.6.3", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON26, flavor.getLanguageLevel(mockSdk));
  }

  public void testPyPy23VersionString() {
    final PythonSdkFlavor flavor = PyPySdkFlavor.INSTANCE;
    final String versionOutput = "Python 2.7.6 (32f35069a16d819b58c1b6efb17c44e3e53397b2, Jun 10 2014, 00:42:27)\n" +
                                 "[PyPy 2.3.1 with GCC 4.8.2]\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("PyPy 2.3.1 [Python 2.7.6]", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON27, flavor.getLanguageLevel(mockSdk));
    assertEquals("__builtin__.py", PythonSdkType.getBuiltinsFileName(mockSdk));
  }

  public void testPyPy323VersionString() {
    final PythonSdkFlavor flavor = PyPySdkFlavor.INSTANCE;
    final String versionOutput = "Python 3.2.5 (986752d005bb6c65ce418113e4c3cd115f61a9b4, Jun 23 2014, 00:23:34)\n" +
                                 "[PyPy 2.3.1 with GCC 4.8.2]\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("PyPy 2.3.1 [Python 3.2.5]", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON32, flavor.getLanguageLevel(mockSdk));
    assertEquals("builtins.py", PythonSdkType.getBuiltinsFileName(mockSdk));
  }

  // TODO: Add tests for MayaPy and IronPython SDK flavors

  @NotNull
  private static Sdk createMockSdk(@NotNull PythonSdkFlavor flavor, @NotNull String versionOutput) {
    final String versionString = flavor.getVersionStringFromOutput(versionOutput);
    final ProjectJdkImpl sdk = new ProjectJdkImpl("Test", PythonSdkType.getInstance(), "/path/to/sdk", versionString);
    sdk.setSdkAdditionalData(new PythonSdkAdditionalData(flavor));
    return sdk;
  }
}
