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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

public class PySdkFlavorTest extends PyTestCase {
  public void testPython27VersionString() {
    testVersionAndFlavor("Python 2.7.6\n", "Python 2.7.6", LanguageLevel.PYTHON27);
  }

  public void testPython34VersionString() {
    testVersionAndFlavor("Python 3.4.0\n", "Python 3.4.0", LanguageLevel.PYTHON34);
  }

  public void testGraalPyVersionString() {
    testVersionAndFlavor("GraalPy 3.12.8 (Interpreted JVM Development Build)\n", "GraalPy 3.12.8", LanguageLevel.PYTHON312);
  }

  private void testVersionAndFlavor(
    @NotNull String versionOutput, @NotNull String expectedVersionString, @NotNull LanguageLevel expectedLanguageLevel
  ) {
    final PythonSdkFlavor<?> flavor = UnixPythonSdkFlavor.getInstance();
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals(expectedVersionString, mockSdk.getVersionString());
    assertEquals(expectedLanguageLevel, flavor.getLanguageLevel(mockSdk));
  }


  // TODO: Add tests for MayaPy and IronPython SDK flavors

  @NotNull
  private Sdk createMockSdk(@NotNull PythonSdkFlavor flavor, @NotNull String versionOutput) {
    final String versionString = PythonSdkFlavor.getVersionStringFromOutput(versionOutput);
    final Sdk sdk = ProjectJdkTable.getInstance().createSdk("Test", PythonSdkType.getInstance());
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath("/path/to/sdk");
    sdkModificator.setVersionString(versionString);
    sdkModificator.setSdkAdditionalData(new PythonSdkAdditionalData(flavor));
    ApplicationManager.getApplication().runWriteAction(() -> {
      sdkModificator.commitChanges();
    });

    if (sdk instanceof Disposable disposableSdk) {
      disposeOnTearDown(disposableSdk);
    }
    return sdk;
  }
}
