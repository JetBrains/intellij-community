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
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger.Frame;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;

import javax.rmi.PortableRemoteObject;
import java.rmi.RemoteException;
import java.util.List;

class RemoteFrameImpl extends PortableRemoteObject implements RemoteDebugger.Frame {
  private final Frame myFrame;
  private final String myAccessToken;

  private RemoteFrameImpl(Frame frame, String accessToken) throws RemoteException {
    myFrame = frame;
    myAccessToken = accessToken;
  }

  public int getLineNumber() {
    return myFrame.getLineNumber();
  }

  public String getURI() {
    return myFrame.getURI();
  }

  public RemoteDebugger.Frame getNext() throws RemoteException {
    return create(myFrame.getNext(), myAccessToken);
  }

  public RemoteDebugger.Frame getPrevious() throws RemoteException {
    return create(myFrame.getPrevious(), myAccessToken);
  }

  public String getXPath() throws RemoteException {
    return ((Debugger.SourceFrame)myFrame).getXPath();
  }

  public ValueImpl eval(String expr, String accessToken) throws Debugger.EvaluationException, RemoteException {
    if (!myAccessToken.equals(accessToken)) throw new RemoteException("Access denied");
    final Value value = ((Debugger.StyleFrame)myFrame).eval(expr);
    return new ValueImpl(value.getValue(), value.getType());
  }

  public List<RemoteDebugger.Variable> getVariables() throws RemoteException {
    return RemoteVariableImpl.convert(((Debugger.StyleFrame)myFrame).getVariables());
  }

  public String getInstruction() throws RemoteException {
    return ((Debugger.StyleFrame)myFrame).getInstruction();
  }

  public static RemoteFrameImpl create(Frame frame, String accessToken) throws RemoteException {
    return frame != null ? new RemoteFrameImpl(frame, accessToken) : null;
  }
}
