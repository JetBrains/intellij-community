package de.plushnikov;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Simple extension of {@link LightJavaCodeInsightFixtureTestCase} that allows skipping test cases
 * based on required Platform API version.
 *
 * @author Alexej Kubarev
 */
public abstract class ApiVersionAwareLightJavaCodeInsightFixtureTestCase extends LightJavaCodeInsightFixtureTestCase {

  private String getMinVersion() {
    RequiredApiVersion requiredVersionAnnotation = getClass().getAnnotation(RequiredApiVersion.class);
    if (requiredVersionAnnotation != null) {
      return requiredVersionAnnotation.value();
    }
    return getCurrentVersion().asStringWithoutProductCode();
  }

  @Override
  protected void runTest() throws Throwable {

    // Minimal API Version for these tests to pass
    BuildNumber required = BuildNumber.fromString(getMinVersion());
    if (getCurrentVersion().compareTo(required) >= 0) {
      super.runTest();
    }
  }

  private BuildNumber getCurrentVersion() {
    return ApplicationInfo.getInstance().getBuild();
  }
}
