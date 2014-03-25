package com.jetbrains.env.python.dotNet;

import com.jetbrains.env.python.debug.PyEnvTestCase;

/**
 * IronPython.NET specific tests
 * @author Ilya.Kazakevich
 */
public class PyIronPythonTest extends PyEnvTestCase {

  /**
   * Tests skeleton generation
   */
  public void testSkeletons() throws Exception {
    runPythonTest(new SkeletonTestTask());
  }
}
