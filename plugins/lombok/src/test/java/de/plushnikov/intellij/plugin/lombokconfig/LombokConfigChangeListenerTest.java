package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.lombokconfig.LombokConfigChangeListener.CONFIG_CHANGE_TRACKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LombokConfigChangeListenerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testConfigChangeIncrementsTracker() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile configFile = myFixture.addFileToProject("lombok.config", "lombok.accessors.chain = true").getVirtualFile();

    long before = CONFIG_CHANGE_TRACKER.getModificationCount();
    WriteAction.runAndWait(() -> VfsUtil.saveText(configFile, "lombok.accessors.chain = false"));

    assertTrue(CONFIG_CHANGE_TRACKER.getModificationCount() > before);
  }

  @Test
  public void testJavaChangeKeepsTracker() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile javaFile = myFixture.addFileToProject("Foo.java", "class Foo {}").getVirtualFile();

    long before = CONFIG_CHANGE_TRACKER.getModificationCount();
    WriteAction.runAndWait(() -> VfsUtil.saveText(javaFile, "class Foo { int x; }"));

    assertEquals(before, CONFIG_CHANGE_TRACKER.getModificationCount());
  }
}
