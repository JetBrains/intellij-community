package com.jetbrains.python.console.parsing;

import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class IPythonData {
  private boolean myEnabled;
  private boolean myAutomagic;
  private Set<String> myMagicCommands = Sets.newHashSet();

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

  public void setMagicCommands(List<String> magicCommands) {
    myMagicCommands.clear();
    myMagicCommands.addAll(magicCommands);
  }
  
  public boolean isMagicCommand(String command) {
    return myMagicCommands.contains(command);
  }
}
