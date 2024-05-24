package de.plushnikov.intellij.plugin;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class IncompleteAndDumbModeRunner extends BlockJUnit4ClassRunner {

  public @interface InsertModeType {
  }




  public IncompleteAndDumbModeRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
  }


}
