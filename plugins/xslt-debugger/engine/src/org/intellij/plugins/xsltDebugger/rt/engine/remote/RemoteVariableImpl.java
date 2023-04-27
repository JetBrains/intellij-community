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

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public final class RemoteVariableImpl extends UnicastRemoteObject implements RemoteDebugger.Variable {
  private final Debugger.Variable myVariable;

  RemoteVariableImpl(Debugger.Variable variable) throws RemoteException {
    myVariable = variable;
  }

  @Override
  public String getURI() {
    return myVariable.getURI();
  }

  @Override
  public int getLineNumber() {
    return myVariable.getLineNumber();
  }

  @Override
  public boolean isGlobal() {
    return myVariable.isGlobal();
  }

  @Override
  public Debugger.Variable.Kind getKind() {
    return myVariable.getKind();
  }

  @Override
  public String getName() {
    return myVariable.getName();
  }

  @Override
  public Value getValue() {
    final Value value = myVariable.getValue();
    return new ValueImpl(value.getValue(), value.getType());
  }

  public static List<RemoteDebugger.Variable> convert(List<? extends Debugger.Variable> list) throws RemoteException {
    List<RemoteDebugger.Variable> variables = new ArrayList<>(list.size());
    for (final Debugger.Variable variable : list) {
      variables.add(new RemoteVariableImpl(variable));
    }
    return variables;
  }
}
