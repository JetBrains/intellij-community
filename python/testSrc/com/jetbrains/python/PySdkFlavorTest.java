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
    final PythonSdkFlavor flavor = UnixPythonSdkFlavor.getInstance();
    final String versionOutput = "Python 2.7.6\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("Python 2.7.6", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON27, flavor.getLanguageLevel(mockSdk));
  }

  public void testPython34VersionString() {
    final PythonSdkFlavor flavor = UnixPythonSdkFlavor.getInstance();
    final String versionOutput = "Python 3.4.0\n";
    final Sdk mockSdk = createMockSdk(flavor, versionOutput);
    assertEquals("Python 3.4.0", mockSdk.getVersionString());
    assertEquals(LanguageLevel.PYTHON34, flavor.getLanguageLevel(mockSdk));
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
