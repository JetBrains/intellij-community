package com.intellij.openapi.vcs;


public class VcsShowConfirmationOptionImpl extends VcsAbstractSetting implements VcsShowConfirmationOption{
  private Value myValue = Value.SHOW_CONFIRMATION;

  private final String myCaption;

  private final String myDoNothingCaption;
  private final String myShowConfirmationCaption;
  private final String myDoActionSilentlyCaption;

  public VcsShowConfirmationOptionImpl(final String displayName,
                                       final String caption,
                                       final String doNothingCaption,
                                       final String showConfirmationCaption,
                                       final String doActionSilentlyCaption) {
    super(displayName);
    myCaption = caption;
    myDoNothingCaption = doNothingCaption;
    myShowConfirmationCaption = showConfirmationCaption;
    myDoActionSilentlyCaption = doActionSilentlyCaption;
  }

  public Value getValue() {
    return myValue;
  }

  public void setValue(Value value) {
    myValue = value;
  }
}
