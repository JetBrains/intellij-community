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

package org.intellij.plugins.xsltDebugger.rt.engine.remote;

import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;

import javax.rmi.PortableRemoteObject;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 28.05.2007
 */
class RemoteVariableImpl extends PortableRemoteObject implements RemoteDebugger.Variable {
  private final Debugger.Variable myVariable;

  public RemoteVariableImpl(Debugger.Variable variable) throws RemoteException {
    myVariable = variable;
  }

  public String getURI() throws RemoteException {
    return myVariable.getURI();
  }

  public int getLineNumber() throws RemoteException {
    return myVariable.getLineNumber();
  }

  public boolean isGlobal() {
    return myVariable.isGlobal();
  }

  public Debugger.Variable.Kind getKind() throws RemoteException {
    return myVariable.getKind();
  }

  public String getName() {
    return myVariable.getName();
  }

  public Value getValue() {
    final Value value = myVariable.getValue();
    return new ValueImpl(value.getValue(), value.getType());
  }

  static List<RemoteDebugger.Variable> convert(List<Debugger.Variable> list) throws RemoteException {
    final ArrayList<RemoteDebugger.Variable> variables = new ArrayList<RemoteDebugger.Variable>(list.size());
    for (final Debugger.Variable variable : list) {
      variables.add(new RemoteVariableImpl(variable));
    }
    return variables;
  }
}
