package com.intellij.properties;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.lang.properties.PropertiesFileType;

/**
 * @author Anna Bulenkova
 */
public class PropertiesCoreEnvironment {
  public static class ApplicationEnvironment {
    public ApplicationEnvironment(CoreApplicationEnvironment appEnvironment) {
      appEnvironment.registerFileType(PropertiesFileType.INSTANCE, "properties");
    }
  }

  public static class ProjectEnvironment {
    public ProjectEnvironment(CoreProjectEnvironment projectEnvironment) {
    }
  }
}
