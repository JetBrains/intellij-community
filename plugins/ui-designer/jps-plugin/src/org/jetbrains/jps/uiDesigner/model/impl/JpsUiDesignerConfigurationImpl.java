package org.jetbrains.jps.uiDesigner.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;

public class JpsUiDesignerConfigurationImpl extends JpsElementBase<JpsUiDesignerConfigurationImpl> implements JpsUiDesignerConfiguration {
  public static final JpsElementChildRole<JpsUiDesignerConfiguration> ROLE = JpsElementChildRoleBase.create("ui designer configuration");
  private final UiDesignerConfigurationState myState = new UiDesignerConfigurationState();

  public JpsUiDesignerConfigurationImpl() {
  }

  public JpsUiDesignerConfigurationImpl(final UiDesignerConfigurationState state) {
    myState.INSTRUMENT_CLASSES = state.INSTRUMENT_CLASSES;
    myState.COPY_FORMS_RUNTIME_TO_OUTPUT = state.COPY_FORMS_RUNTIME_TO_OUTPUT;
    myState.USE_DYNAMIC_BUNDLES = state.USE_DYNAMIC_BUNDLES;
  }

  public UiDesignerConfigurationState getState() {
    return myState;
  }

  @NotNull
  @Override
  public JpsUiDesignerConfigurationImpl createCopy() {
    return new JpsUiDesignerConfigurationImpl(myState);
  }

  @Override
  public boolean isCopyFormsRuntimeToOutput() {
    return myState.COPY_FORMS_RUNTIME_TO_OUTPUT;
  }

  @Override
  public void setCopyFormsRuntimeToOutput(boolean value) {
    if (myState.COPY_FORMS_RUNTIME_TO_OUTPUT != value) {
      myState.COPY_FORMS_RUNTIME_TO_OUTPUT = value;
      fireElementChanged();
    }
  }

  @Override
  public boolean isInstrumentClasses() {
    return myState.INSTRUMENT_CLASSES;
  }

  @Override
  public void setInstrumentClasses(boolean value) {
    if (myState.INSTRUMENT_CLASSES != value) {
      myState.INSTRUMENT_CLASSES = value;
      fireElementChanged();
    }
  }

  @Override
  public void setUseDynamicBundles(boolean value) {
    myState.USE_DYNAMIC_BUNDLES = value;
  }

  @Override
  public boolean isUseDynamicBundles() {
    return myState.USE_DYNAMIC_BUNDLES;
  }

  public static class UiDesignerConfigurationState {
    public boolean INSTRUMENT_CLASSES = true;
    public boolean COPY_FORMS_RUNTIME_TO_OUTPUT = true;
    public boolean USE_DYNAMIC_BUNDLES = false;
  }
}
