/*
 * Copyright 2002-2007 Sascha Weinreuter
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
import org.intellij.plugins.xsltDebugger.rt.engine.OutputEventQueue;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 08.06.2007
 */
public class OutputEventQueueImpl implements OutputEventQueue {
  private final Debugger myDebugger;

  private final List<NodeEvent> myEvents = new ArrayList<NodeEvent>();

  private boolean myEnabled = true;

  public OutputEventQueueImpl(Debugger debugger) {
    myDebugger = debugger;
  }

  public void startDocument() {
    if (myEnabled) {
      myEvents.add(new NodeEvent(START_DOCUMENT, null, null));
    }
  }

  public void endDocument() {
    if (myEnabled) {
      myEvents.add(new NodeEvent(END_DOCUMENT, null, null));
    }
  }

  public void startElement(String prefix, String localName, String uri) {
    addEvent(new NodeEvent(OutputEventQueue.START_ELEMENT, new NodeEvent.QName(prefix, localName, uri), null));
  }

  public void attribute(String prefix, String localName, String uri, String value) {
    addEvent(new NodeEvent(ATTRIBUTE, new NodeEvent.QName(prefix, localName, uri), value));
  }

  public void endElement() {
    addEvent(new NodeEvent(END_ELEMENT, null, null));
  }

  public void characters(String s) {
    addEvent(new NodeEvent(CHARACTERS, null, s));
  }

  public void comment(String s) {
    addEvent(new NodeEvent(COMMENT, null, s));
  }

  public void pi(String target, String data) {
    addEvent(new NodeEvent(PI, new NodeEvent.QName(target), data));
  }

  public void trace(String text) {
    addEvent(new NodeEvent(TRACE_POINT, null, text));
  }

  public void setEnabled(boolean b) {
    myEnabled = b;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  private void addEvent(NodeEvent event) {
    if (myEnabled) {
      final Debugger.StyleFrame frame = myDebugger.getCurrentFrame();
      if (frame != null) {
        event.setLocation(frame.getURI(), frame.getLineNumber());
      }
      myEvents.add(event);
    }
  }

  public List<NodeEvent> getEvents() {
    try {
      return new ArrayList<NodeEvent>(myEvents);
    } finally {
      myEvents.clear();
    }
  }
}
