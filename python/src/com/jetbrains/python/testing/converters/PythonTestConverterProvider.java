package com.jetbrains.python.testing.converters;

import com.intellij.conversion.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonTestConverterProvider extends ConverterProvider {
  public PythonTestConverterProvider() {
    super("PythonTestConverterProvider");
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "Test run configurations will be updated";
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new ProjectConverter() {
      @Override
      public ConversionProcessor<RunManagerSettings> createRunConfigurationsConverter() {
        return new PythonTestRunConfigurationsConverter();
      }
    };
  }
}
