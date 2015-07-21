package org.jetbrains.jps.maven.model;

import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsSerializationTestCase;

import java.util.List;

/**
 * @author nik
 */
public class JpsMavenModuleSerializationTest extends JpsSerializationTestCase {
  public void testLoadProject() {
    loadProject("plugins/maven/jps-plugin/testData/compiler/classpathTest");
    List<JpsModule> modules = myProject.getModules();
    assertEquals(3, modules.size());

    JpsModule main = null;
    JpsModule dep = null;
    JpsModule depTest = null;
    for (JpsModule module : modules) {
      final String name = module.getName();
      if ("main".equals(name)) {
        main = module;
      }
      else if ("dep-test".equals(name)) {
        depTest = module;
      }
      else if ("dep".equals(name)) {
        dep = module;
      }
      else {
        fail("Unexpected module name " + name);
      }
    }
    assertNotNull("module 'main' was not loaded",  main);
    assertNotNull("module 'depTest' was not loaded", depTest);
    assertNotNull("module 'dep' was not loaded", dep);


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
