package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import static de.plushnikov.intellij.plugin.lombokconfig.LombokConfigChangeListener.CONFIG_CHANGE_TRACKER;

public class LombokConfigChangeListenerTest extends LightJavaCodeInsightFixtureTestCase {

  public void testConfigChangeIncrementsTracker() throws Exception {
    VirtualFile configFile = myFixture.addFileToProject("lombok.config", "lombok.accessors.chain = true").getVirtualFile();

    long before = CONFIG_CHANGE_TRACKER.getModificationCount();
    WriteAction.runAndWait(() -> VfsUtil.saveText(configFile, "lombok.accessors.chain = false"));

    assertTrue(CONFIG_CHANGE_TRACKER.getModificationCount() > before);
  }

  public void testJavaChangeKeepsTracker() throws Exception {
    VirtualFile javaFile = myFixture.addFileToProject("Foo.java", "class Foo {}").getVirtualFile();

    long before = CONFIG_CHANGE_TRACKER.getModificationCount();
    WriteAction.runAndWait(() -> VfsUtil.saveText(javaFile, "class Foo { int x; }"));

    assertEquals(before, CONFIG_CHANGE_TRACKER.getModificationCount());
  }
}
