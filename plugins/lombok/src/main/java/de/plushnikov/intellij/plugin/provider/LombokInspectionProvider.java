package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInspection.InspectionToolProvider;
import de.plushnikov.intellij.plugin.inspection.DeprecatedLombokAnnotationInspection;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import de.plushnikov.intellij.plugin.inspection.RedundantModifiersOnValueLombokAnnotationInspection;
import org.jetbrains.annotations.NotNull;

public class LombokInspectionProvider implements InspectionToolProvider {

  @NotNull
  @Override
  public Class[] getInspectionClasses() {
    return new Class[]{LombokInspection.class, DeprecatedLombokAnnotationInspection.class,
      RedundantModifiersOnValueLombokAnnotationInspection.class};
  }
}
