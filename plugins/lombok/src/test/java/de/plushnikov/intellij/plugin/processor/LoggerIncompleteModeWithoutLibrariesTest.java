package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.TestApplication;
import de.plushnikov.intellij.plugin.LombokParsingOptions;
import org.jetbrains.annotations.NotNull;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes,
 * without actual libraries for incomplete mode
 */
@TestApplication
public class LoggerIncompleteModeWithoutLibrariesTest extends LoggerTest {

  @Override
  protected @NotNull LombokParsingOptions parsingOptions() {
    return new LombokParsingOptions().incompleteDescriptor(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST);
  }
}
