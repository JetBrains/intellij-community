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
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.util.io.FileUtil;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.easymock.MockType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
public final class WinPythonSdkFlavorTest {

  private IMocksControl myControl;
  private WinRegistryService myMock;

  @Before
  public void setUp() {
    myControl = EasyMock.createControl(MockType.NICE);
    myMock = myControl.createMock(WinRegistryService.class);
  }

  /**
   * Check PyCharm can find python using registry and pep-514
   */
  @Test
  public void testFindPythonUsingRegistry() throws Exception {


    EasyMock.expect(myMock.listBranches(caseInsensitive("HKEY_CURRENT_USER\\Software\\Python")))
      .andReturn(Arrays.asList("PythonCore", "CompanyFoo")).anyTimes();


    EasyMock.expect(myMock.listBranches(caseInsensitive("HKEY_CURRENT_USER\\Software\\Python\\PythonCore")))
      .andReturn(Collections.singletonList("3.5")).anyTimes();

    final String tempDir = File.createTempFile("foo", "bar").getParent();
    final File pythonFile = new File(tempDir, "python.exe");
    FileUtil.appendToFile(pythonFile, "");
    assert pythonFile.exists() : "Failed to create file";

    EasyMock.expect(myMock.getDefaultKey(caseInsensitive("HKEY_CURRENT_USER\\Software\\Python\\PythonCore\\3.5\\InstallPath")))
      .andReturn(tempDir).anyTimes();


    EasyMock.expect(myMock.listBranches(EasyMock.anyString())).andReturn(Collections.emptyList()).anyTimes();

    myControl.replay();

    final WinPythonSdkFlavor systemUnderTest = new WinPythonSdkFlavor(myMock);
    final List<String> result = new ArrayList<>();
    systemUnderTest.findInRegistry(result);
    Assert.assertThat("Python should be found by registry, but failed to do so", result,
                      Matchers.containsInAnyOrder(pythonFile.getAbsolutePath()));
  }

  /**
   * @return case insensitive matcher for string
   */
  private String caseInsensitive(@NotNull final String source) {
    // Never returns null: lame EasyMock api
    //noinspection ConstantConditions
    return EasyMock.matches(String.format("(?i)%s", source.replace("\\", "\\\\")));
  }
}