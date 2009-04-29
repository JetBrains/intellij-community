package com.intellij.spellchecker.inspections;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.spellchecker.inspections.common.CommentsWithMistakesInspection;
import com.intellij.spellchecker.inspections.common.XmlWithMistakesInspection;
import com.intellij.spellchecker.inspections.java.*;

/**
 * Inspection tool provider.
 */
public class SpellCheckerInspectionToolProvider implements InspectionToolProvider {
  private static final Class[] INSPECTIONS =
    {CommentsWithMistakesInspection.class, ClassNameWithMistakesInspection.class, MethodNameWithMistakesInspection.class,
      FieldNameWithMistakesInspection.class, LocalVariableNameWithMistakesInspection.class, 
      XmlWithMistakesInspection.class, DocCommentWithMistakesInspection.class};

  public Class[] getInspectionClasses() {
    return INSPECTIONS;
  }
}
