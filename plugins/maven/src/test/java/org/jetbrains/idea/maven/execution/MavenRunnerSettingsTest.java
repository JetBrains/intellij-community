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
