/*
 * User: anna
 * Date: 15-Aug-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;

public abstract class TestNGConfigurationProducer extends JavaRuntimeConfigurationProducerBase implements Cloneable {

  public TestNGConfigurationProducer() {
    super(TestNGConfigurationType.getInstance());
  }
}