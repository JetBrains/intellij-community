package com.jetbrains.env.python.dotNet;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * IronPython.NET specific tests
 *
 * @author Ilya.Kazakevich
 */
public class PyIronPythonTest extends PyEnvTestCase {

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
  public void testSkeletons() throws Exception {
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
  public void testClassFromModule() throws Exception {
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
  public void testClassFromModuleAlias() throws Exception {
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
  public void testModuleFromPackage() throws Exception {
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
  public void testSeveralClasses() throws Exception {
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
  public void testImportBuiltInSystem() throws Exception {
    final SkeletonTestTask task = new SkeletonTestTask(
      null,
      "System.Web",
      "import_system.py",
      null
    );
    runPythonTest(task);
    final PyFile skeleton = task.getGeneratedSkeleton();
    new ReadAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        Assert.assertNotNull("System.Web does not contain class AspNetHostingPermissionLevel. Error generating stub? It has classes  " +
                             skeleton.getTopLevelClasses(),
                             skeleton.findTopLevelClass("AspNetHostingPermissionLevel"));
      }
    }.execute();

  }

  /**
   * Test importing of inner classes
   */
  public void testImportInnerClass() throws Exception {
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
  public void testWholeNameSpace() throws Exception {
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
  public void testSingleClass() throws Exception {
    runPythonTest(new SkeletonTestTask(
      "dotNet/expected.skeleton.SingleNameSpace.py",
      "SingleNameSpace",
      "single_class.py",
      null
    ));
  }
}
