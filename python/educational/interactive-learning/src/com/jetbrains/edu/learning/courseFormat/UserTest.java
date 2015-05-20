package com.jetbrains.edu.learning.courseFormat;

public class UserTest {
  private String input;
  private String output;
  private StringBuilder myInputBuffer = new StringBuilder();
  private StringBuilder myOutputBuffer =  new StringBuilder();
  private boolean myEditable = false;

  public String getInput() {
    return input;
  }

  public void setInput(String input) {
    this.input = input;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String output) {
    this.output = output;
  }

  public StringBuilder getInputBuffer() {
    return myInputBuffer;
  }

  public StringBuilder getOutputBuffer() {
    return myOutputBuffer;
  }

  public boolean isEditable() {
    return myEditable;
  }

  public void setEditable(boolean editable) {
    myEditable = editable;
  }
}
