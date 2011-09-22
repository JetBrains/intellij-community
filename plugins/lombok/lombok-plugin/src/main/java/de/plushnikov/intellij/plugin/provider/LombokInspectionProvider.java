package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInspection.InspectionToolProvider;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

public class LombokInspectionProvider implements InspectionToolProvider {

  @Override
  public Class[] getInspectionClasses() {
    return new Class[]{LombokInspection.class};
  }
}
