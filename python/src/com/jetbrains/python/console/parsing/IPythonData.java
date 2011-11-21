package com.jetbrains.python.console.parsing;

/**
 * @author traff
 */
public class IPythonData {
  private boolean myEnabled;
  private boolean myAutomagic;

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public boolean isAutomagic() {
    return myAutomagic;
  }

  public void setAutomagic(boolean automagic) {
    myAutomagic = automagic;
  }
}
