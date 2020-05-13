package org.jetbrains.jps.uiDesigner.model;

import org.jetbrains.jps.model.JpsElement;

public interface JpsUiDesignerConfiguration extends JpsElement {
  boolean isCopyFormsRuntimeToOutput();

  void setCopyFormsRuntimeToOutput(boolean value);

  boolean isInstrumentClasses();

  void setInstrumentClasses(boolean value);

  void setUseDynamicBundles(boolean value);
  
  boolean isUseDynamicBundles();
}
