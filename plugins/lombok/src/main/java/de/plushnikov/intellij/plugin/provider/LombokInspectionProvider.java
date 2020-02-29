package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import de.plushnikov.intellij.plugin.inspection.DeprecatedLombokAnnotationInspection;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import de.plushnikov.intellij.plugin.inspection.RedundantModifiersOnValueLombokAnnotationInspection;
import org.jetbrains.annotations.NotNull;

public class LombokInspectionProvider implements InspectionToolProvider {

  @NotNull
  @Override
  public Class<? extends LocalInspectionTool>[] getInspectionClasses() {
    return new Class[]{LombokInspection.class, DeprecatedLombokAnnotationInspection.class,
      RedundantModifiersOnValueLombokAnnotationInspection.class};
  }
}
