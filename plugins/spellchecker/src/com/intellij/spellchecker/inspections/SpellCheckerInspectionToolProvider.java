package com.intellij.spellchecker.inspections;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Inspection tool provider.
 */
public class SpellCheckerInspectionToolProvider implements InspectionToolProvider {

  private static final Class[] INSPECTIONS =
    {SpellCheckingInspection.class};

  public Class[] getInspectionClasses() {
    return INSPECTIONS;

  }

  public static LocalInspectionTool[] getInspectionTools() {
    return new LocalInspectionTool[]{new SpellCheckingInspection()};

  }
}
