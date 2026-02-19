package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes,
 * without actual libraries for incomplete mode
 */
public class LoggerIncompleteModeWithoutLibrariesTest extends LoggerTest {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptorForIncompleteMode() {
    return JAVA_LATEST;
  }
}
