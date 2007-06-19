/*
 * User: anna
 * Date: 18-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.InspectionToolProvider;

public class TestNGInspectionsToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
      return new Class[]{JUnitConvertTool.class,
        ConvertOldAnnotationInspection.class,
        ConvertJavadocInspection.class,
        ConvertAnnotationInspection.class,
        DependsOnMethodInspection.class,
        DependsOnGroupsInspection.class,
        UndeclaredTestInspection.class
      };
    }
}