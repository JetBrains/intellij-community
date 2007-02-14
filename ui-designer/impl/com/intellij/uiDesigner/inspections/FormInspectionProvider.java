package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.InspectionToolProvider;

/**
 * @author yole
 */
public class FormInspectionProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[] {
      DuplicateMnemonicInspection.class,
      MissingMnemonicInspection.class,
      NoLabelForInspection.class,
      NoButtonGroupInspection.class,
      OneButtonGroupInspection.class,
      NoScrollPaneInspection.class,
      BoundFieldAssignmentInspection.class
    };
  }
}
