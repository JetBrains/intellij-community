/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.rt.engine.local;

import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 23.05.2007
 */
public class VariableImpl implements Debugger.Variable {
  private final boolean myGlobal;
  private final Kind myKind;
  private final String myRealname;
  private final Value myValue;
  private final String myUri;
  private final int myLineNumber;

  public VariableImpl(String realname, Value value, boolean global, Kind kind, String uri, int lineNumber) {
    myValue = value;
    myRealname = realname;
    myGlobal = global;
    myKind = kind;
    myUri = uri;
    myLineNumber = lineNumber;
  }

  public String getURI() {
    return myUri;
  }

  public int getLineNumber() {
    return myLineNumber;
  }

  public String getName() {
    return myRealname;
  }

  public Value getValue() {
    return myValue;
  }

  public boolean isGlobal() {
    return myGlobal;
  }

  public Kind getKind() {
    return myKind;
  }

  @Override
  public String toString() {
    return (myGlobal ? "global:" : "") + "{" + myKind + ":" + myRealname + "=" + myValue + "}";
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VariableImpl that = (VariableImpl)o;

    return myRealname.equals(that.myRealname);
  }

  public int hashCode() {
    return myRealname.hashCode();
  }
}
