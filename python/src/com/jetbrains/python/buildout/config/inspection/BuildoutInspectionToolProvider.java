package com.jetbrains.python.buildout.config.inspection;

import com.intellij.codeInspection.InspectionToolProvider;

/**
 * @author traff
 */
public class BuildoutInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[]
      {
        BuildoutUnresolvedPartInspection.class
      };
  }
}
