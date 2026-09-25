package org.jetbrains.jps.maven.model;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.testFramework.common.BazelTestUtil;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsProjectData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpsMavenModuleSerializationTest {
  @Test
  public void testLoadProject() {
    var testDataRoot = BazelTestUtil.isUnderBazelTest()
                       ? BazelTestUtil.getFileFromBazelRuntime(BazelLabel.Companion.fromString("@community//plugins/maven/jps:testData"))
                       : PathManagerEx.findFileUnderCommunityHome("plugins/maven/jps/testData").toPath();
    JpsProjectData projectData =
      JpsProjectData.loadFromTestData(testDataRoot.resolve("compiler/classpathTest").toAbsolutePath().toString(), getClass());
    List<JpsModule> modules = projectData.getProject().getModules();
    assertEquals(3, modules.size());
    JpsModule dep = modules.get(0);
    assertEquals("dep", dep.getName());
    JpsModule depTest = modules.get(1);
    assertEquals("dep-test", depTest.getName());
    JpsModule main = modules.get(2);
    assertEquals("main", main.getName());

    for (JpsModule module : modules) {
      assertNotNull(getService().getExtension(module));
    }
    List<JpsDependencyElement> dependencies = main.getDependenciesList().getDependencies();
    assertEquals(5, dependencies.size());
    assertTrue(getService().isProductionOnTestDependency(dependencies.get(3)));
    assertFalse(getService().isProductionOnTestDependency(dependencies.get(4)));
  }

  private static JpsMavenExtensionService getService() {
    return JpsMavenExtensionService.getInstance();
  }
}
