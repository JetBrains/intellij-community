package org.jetbrains.idea.maven.runner;

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
    ProjectJdkTable.getInstance().addJdk(createJdk("Java 1.3"));
    ProjectJdkTable.getInstance().addJdk(createJdk("Java 1.5"));
    ProjectJdkTable.getInstance().addJdk(createJdk("Java 1.4"));

    MavenRunnerSettings settings = new MavenRunnerSettings();
    assertEquals("Java 1.5", settings.getJreName());
  }

  public void testUsingInternalJdkIfNoOtherIsDefined() throws Exception {
    MavenRunnerSettings settings = new MavenRunnerSettings();
    assertEquals(MavenRunnerSettings.USE_INTERNAL_JAVA, settings.getJreName());
  }
}
