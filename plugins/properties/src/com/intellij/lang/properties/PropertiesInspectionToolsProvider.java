package com.intellij.lang.properties;

import com.intellij.codeInspection.InspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 11.02.2009
 * Time: 18:23:29
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesInspectionToolsProvider implements InspectionToolProvider{
  public Class[] getInspectionClasses() {
    return new Class[] {
      UnusedPropertyInspection.class,
    };
  }
}
