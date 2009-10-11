/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import org.jetbrains.idea.maven.MavenImportingTestCase;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.application.ApplicationManager;

public class MavenRunnerSettingsTest extends MavenImportingTestCase {
  private Sdk[] myOldJdks;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myOldJdks = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk each : myOldJdks) {
      ProjectJdkTable.getInstance().removeJdk(each);
    }
  }

  @Override
  protected void tearDown() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Sdk each : myOldJdks) {
          ProjectJdkTable.getInstance().addJdk(each);
        }
      }
    });
    super.tearDown();
  }

  public void testUsingLatestAvailableJdk() throws Exception {
    Sdk jdk3 = createJdk("Java 1.3");
    Sdk jdk4 = createJdk("Java 1.4");
    Sdk jdk5 = createJdk("Java 1.5");
    ProjectJdkTable.getInstance().addJdk(jdk3);
    ProjectJdkTable.getInstance().addJdk(jdk5);
    ProjectJdkTable.getInstance().addJdk(jdk4);

    try {
      MavenRunnerSettings settings = new MavenRunnerSettings();
      assertEquals("Java 1.5", settings.getJreName());
    }
    finally {
      ProjectJdkTable.getInstance().removeJdk(jdk3);
      ProjectJdkTable.getInstance().removeJdk(jdk4);
      ProjectJdkTable.getInstance().removeJdk(jdk5);
    }
  }

  public void testUsingInternalJdkIfNoOtherIsDefined() throws Exception {
    MavenRunnerSettings settings = new MavenRunnerSettings();
    assertEquals(MavenRunnerSettings.USE_INTERNAL_JAVA, settings.getJreName());
  }
}
