package de.plushnikov.intellij.plugin.highlights;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;


/**
 * @author Lekanich
 */
public abstract class AbstractLombokHighlightsTest extends LightJavaInspectionTestCase {

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

  @Override
  protected String getBasePath() {
    return "/plugins/lombok/testData/highlights";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return null;
  }
}

