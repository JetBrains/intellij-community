/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console.parsing;

import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PythonConsoleData {
  private boolean myIPythonEnabled;
  private boolean myIPythonAutomagic = true;
  private final Set<String> myIPythonMagicCommands = Sets.newHashSet();
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
