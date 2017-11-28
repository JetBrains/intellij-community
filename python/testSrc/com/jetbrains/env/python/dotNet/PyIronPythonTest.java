package com.jetbrains.env.python.dotNet;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.InvalidSdkException;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * IronPython.NET specific tests
 *
 * @author Ilya.Kazakevich
 */
public final class PyIronPythonTest extends PyEnvTestCase {

  /**
   * IronPython tag
   */
  static final String IRON_TAG = "iron";

  public PyIronPythonTest() {
    super(IRON_TAG);
  }

  /**
   * Tests skeleton generation
   */
  @Test
  public void testSkeletons() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.java.py",
      "com.just.like.java",
      "testSkeleton.py",
      null
    ));
  }

  /**
   * Tests skeleton generation with "from" statements
   */
  @Test
  public void testClassFromModule() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.java.py",
      "com.just.like.java",
      "import_class_from_module.py",
      null
    ));
  }

  /**
   * Tests skeleton generation when imported as alias
   */
  @Test
  public void testClassFromModuleAlias() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.java.py",
      "com.just.like.java",
      "import_class_from_module_alias.py",
      null
    ));
  }

  /**
   * Tests skeleton generation when module is imported
   */
  @Test
  public void testModuleFromPackage() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.java.py",
      "com.just.like.java",
      "import_module_from_package.py",
      null
    ));
  }

  /**
   * Tests skeleton generation when several classes are imported
   */
  @Test
  public void testSeveralClasses() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.java.py",
      "com.just.like.java",
      "import_several_classes_from_module.py",
      "com.just.like.java.LikeJavaClass"
    ));
  }

  /**
   * Tests skeletons for built-in classes. We can't compare files (CLR class may be changed from version to version),
   * but we are sure there should be class System.Web.AspNetHostingPermissionLevel which is part of public API
   */
  @Test
  public void testImportBuiltInSystem() {
    final SkeletonTestTask task = new SkeletonTestTask(
      null,
      "System.Web",
      "import_system.py",
      null
    ) {
      @Override
      public void runTestOn(@NotNull final String sdkHome) throws IOException, InvalidSdkException {
        super.runTestOn(sdkHome);
        ApplicationManager.getApplication().runReadAction(() -> {
          final PyFile skeleton = (PyFile)myFixture.getFile();
          Assert.assertNotNull("System.Web does not contain class AspNetHostingPermissionLevel. Error generating stub? It has classes  " +
                               skeleton.getTopLevelClasses(),
                               skeleton.findTopLevelClass("AspNetHostingPermissionLevel"));
        });
      }
    };
    runPythonTest(task);
  }

  /**
   * Test importing of inner classes
   */
  @Test
  public void testImportInnerClass() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.Deep.py",
      "SingleNameSpace.Some.Deep",
      "inner_class.py",
      null
    ));
  }

  /**
   * Test importing of the whole namespace
   */
  @Test
  public void testWholeNameSpace() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.SingleNameSpace.py",
      "SingleNameSpace",
      "whole_namespace.py",
      null
    ));
  }

  /**
   * Test importing of single class
   */
  @Test
  public void testSingleClass() {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.SingleNameSpace.py",
      "SingleNameSpace",
      "single_class.py",
      null
    ));
  }
}
