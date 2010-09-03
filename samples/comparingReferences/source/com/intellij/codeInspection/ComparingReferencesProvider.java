package com.intellij.codeInspection;

/**
 * @author max
 */
public class ComparingReferencesProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[] { ComparingReferencesInspection.class};
  }
}
