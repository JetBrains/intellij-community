package com.jetbrains.env.python.dotNet;

import com.jetbrains.env.python.debug.PyEnvTestCase;

/**
 * IronPython.NET specific tests
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
    runPythonTest(new SkeletonTestTask());
  }
}
