package de.plushnikov.intellij.plugin.inspection;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public abstract class LombokInspectionTest extends LightJavaInspectionTestCase {
  static final String TEST_DATA_INSPECTION_DIRECTORY = "testData/inspection";

  @Override
  protected String getBasePath() {
    return "/plugins/lombok/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get("platform.random.idempotence.check.rate").setValue(1, getTestRootDisposable());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_DESCRIPTOR;
  }
}
