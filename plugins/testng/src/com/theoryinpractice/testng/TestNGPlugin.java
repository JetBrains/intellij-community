/*
 * User: anna
 * Date: 30-Jul-2007
 */
package com.theoryinpractice.testng;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;

public class TestNGPlugin implements StandardResourceProvider {
  public void registerResources(ResourceRegistrar registrar) {
    registrar.addStdResource("http://testng.org/testng-1.0.dtd", "/resources/standardSchemas/testng-1.0.dtd", getClass());
  }
  
}