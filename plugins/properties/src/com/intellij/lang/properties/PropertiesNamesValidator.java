package com.intellij.lang.properties;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class PropertiesNamesValidator implements NamesValidator {
  public boolean isKeyword(final String name, final Project project) {
    return false;
  }

  public boolean isIdentifier(final String name, final Project project) {
    return true;
  }
}
