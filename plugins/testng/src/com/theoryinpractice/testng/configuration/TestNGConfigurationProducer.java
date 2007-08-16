/*
 * User: anna
 * Date: 15-Aug-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.junit.RuntimeConfigurationProducer;

public abstract class TestNGConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  public static TestNGConfigurationProducer[] PROTOTYPES = new TestNGConfigurationProducer[] {
    new TestNGInClassConfigurationProducer(), new TestNGPackageConfigurationProducer()
  };

  public TestNGConfigurationProducer() {
    super(TestNGConfigurationType.getInstance());
  }
}