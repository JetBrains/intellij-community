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

package org.intellij.plugins.xsltDebugger.rt.engine;

class BreakpointImpl implements Breakpoint {
  private final String myUri;
  private final int myLine;
  private boolean myEnabled;
  private String myCondition;
  private String myLogMsg;
  private String myTraceMsg;
  private boolean mySuspend;

  BreakpointImpl(String uri, int line) {
    myUri = uri;
    myLine = line;
    myEnabled = true;
  }

  @Override
  public void setEnabled(boolean b) {
    myEnabled = b;
  }

  @Override
  public void setCondition(String expr) {
    myCondition = expr;
  }

  @Override
  public void setLogMessage(String expr) {
    myLogMsg = expr;
  }

  @Override
  public String getTraceMessage() {
    return myTraceMsg;
  }

  @Override
  public void setTraceMessage(String expr) {
    myTraceMsg = expr;
  }

  @Override
  public boolean isSuspend() {
    return mySuspend;
  }

  @Override
  public void setSuspend(boolean suspend) {
    mySuspend = suspend;
  }

  @Override
  public String getCondition() {
    return myCondition;
  }

  @Override
  public String getLogMessage() {
    return myLogMsg;
  }

  @Override
  public String getUri() {
    return myUri;
  }

  @Override
  public int getLine() {
    return myLine;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final BreakpointImpl that = (BreakpointImpl)o;

    if (myLine != that.myLine) return false;
    return myUri.equals(that.myUri);
  }

  @Override
  public int hashCode() {
    int result;
    result = myUri.hashCode();
    result = 31 * result + myLine;
    return result;
  }


  @Override
  public String toString() {
    return "Breakpoint{" +
           "myUri='" + myUri + '\'' +
           ", myLine=" + myLine +
           ", myEnabled=" + myEnabled +
           '}';
  }
}
