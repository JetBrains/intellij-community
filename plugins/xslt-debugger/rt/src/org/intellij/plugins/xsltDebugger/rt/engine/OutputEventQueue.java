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

package org.intellij.plugins.xsltDebugger.rt.engine;

import java.io.Serializable;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 08.06.2007
 */
public interface OutputEventQueue {
  int START_DOCUMENT = 0;
  int END_DOCUMENT = 99;
  int START_ELEMENT = 1;
  int END_ELEMENT = 2;
  int ATTRIBUTE = 3;
  int CHARACTERS = 4;
  int COMMENT = 5;
  int PI = 6;

  int TRACE_POINT = 20;

  void setEnabled(boolean b);

  List<NodeEvent> getEvents();

  final class NodeEvent implements Serializable {
    public static final class QName implements Serializable {
      public String myPrefix;
      public String myLocalName;
      public String myURI;

      public QName(String prefix, String localName, String URI) {
        myPrefix = prefix;
        myLocalName = localName;
        myURI = URI;
      }

      public QName(String qName, String uri) {
        myURI = uri;
        final String[] parts = qName.split(":");
        if (parts.length == 2) {
          myPrefix = parts[0];
          myLocalName = parts[1];
        } else {
          myPrefix = null;
          myLocalName = parts[0];
        }
      }

      public QName(String name) {
        myLocalName = name;
        myPrefix = null;
        myURI = null;
      }

      public String getQName() {
        return myPrefix != null && myPrefix.length() > 0 ? myPrefix + ":" + myLocalName : myLocalName;
      }
    }

    private final int myType;

    public String myValue;
    public QName myQName;
    public String myURI;
    private int myLineNumber;

    public NodeEvent(int type, QName qName, String value) {
      myType = type;
      myQName = qName;
      myValue = value;
    }

    public int getType() {
      return myType;
    }

    public QName getQName() {
      return myQName;
    }

    public String getValue() {
      return myValue;
    }

    public void setLocation(String uri, int lineNumber) {
      myURI = uri;
      myLineNumber = lineNumber;
    }

    public int getLineNumber() {
      return myLineNumber;
    }

    public String getURI() {
      return myURI;
    }
  }
}
