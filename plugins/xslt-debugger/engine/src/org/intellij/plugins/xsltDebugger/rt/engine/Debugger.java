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

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 23.05.2007
 */
public interface Debugger extends Watchable {
  enum State {
    CREATED, RUNNING, SUSPENDED, STOPPED
  }

  State getState();

  boolean start();

  void stop(boolean force);

  void step();

  void stepInto();

  void resume();

  void pause();

  boolean isStopped();

  StyleFrame getCurrentFrame();

  SourceFrame getSourceFrame();

  Value eval(String expr) throws EvaluationException;

  List<Variable> getGlobalVariables();

  BreakpointManager getBreakpointManager();

  OutputEventQueue getEventQueue();

  boolean waitForDebuggee();

  State waitForStateChange(State state);

  interface Locatable {
    String getURI();

    int getLineNumber();
  }

  interface Frame<T extends Frame> extends Locatable {
    T getNext();

    T getPrevious();
  }

  interface StyleFrame extends Frame<StyleFrame> {
    String getInstruction();

    Value eval(String expr) throws EvaluationException;

    List<Variable> getVariables();
  }

  interface SourceFrame extends Frame<SourceFrame> {
    String getXPath();
  }

  interface Variable extends Locatable {
    enum Kind {VARIABLE, PARAMETER, EXPRESSION}

    boolean isGlobal();

    Kind getKind();

    String getName();

    Value getValue();
  }

  class EvaluationException extends Exception {
    public EvaluationException(String message) {
      super(message);
    }
  }
}
