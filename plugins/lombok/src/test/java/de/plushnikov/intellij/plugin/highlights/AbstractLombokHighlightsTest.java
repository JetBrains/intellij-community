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
  public static final String TEST_DATA_INSPECTION_DIRECTORY = "testData/highlights";

  @Override
  public void setUp() throws Exception {
    super.setUp();

    LombokTestUtil.loadLombokLibrary(myFixture.getProjectDisposable(), getModule());

    Registry.get("platform.random.idempotence.check.rate").setValue(1, getTestRootDisposable());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.getProjectDescriptor();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return null;
  }
}

