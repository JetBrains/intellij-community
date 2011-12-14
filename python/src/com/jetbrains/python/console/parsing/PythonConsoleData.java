package com.jetbrains.python.console.parsing;

import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PythonConsoleData {
  private boolean myIPythonEnabled;
  private boolean myIPythonAutomagic;
  private Set<String> myIPythonMagicCommands = Sets.newHashSet();
  private int myIndentSize = -1;

  public boolean isIPythonEnabled() {
    return myIPythonEnabled;
  }

  public void setIPythonEnabled(boolean IPythonEnabled) {
    myIPythonEnabled = IPythonEnabled;
  }

  public boolean isIPythonAutomagic() {
    return myIPythonAutomagic;
  }

  public void setIPythonAutomagic(boolean IPythonAutomagic) {
    myIPythonAutomagic = IPythonAutomagic;
  }

  public void setIPythonMagicCommands(List<String> IPythonMagicCommands) {
    myIPythonMagicCommands.clear();
    myIPythonMagicCommands.addAll(IPythonMagicCommands);
  }
  
  public boolean isMagicCommand(String command) {
    return myIPythonMagicCommands.contains(command);
  }

  public int getIndentSize() {
    return myIndentSize;
  }

  public void setIndentSize(int indentSize) {
    myIndentSize = indentSize;
  }
}
