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

import java.io.Serializable;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 31.05.2007
 */
public interface Value extends Serializable {
  interface Type extends Serializable {
    String getName();
  }

  enum XPathType implements Type {
    BOOLEAN, NUMBER, STRING, NODESET, OBJECT, UNKNOWN;

    public String getName() {
      return name().toLowerCase();
    }
  }

  final class ObjectType implements Type {
    private final String myName;

    public ObjectType(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }
  }

  Object getValue();

  Type getType();

  class NodeSet implements Serializable {
    public final String myStringValue;
    private final List<Node> myNodes;

    public NodeSet(String stringValue, List<Node> nodes) {
      myStringValue = stringValue;
      myNodes = nodes;
    }

    public List<Node> getNodes() {
      return myNodes;
    }

    @Override
    public String toString() {
      return myStringValue;
    }
  }

  class Node implements Serializable, Debugger.Locatable {
    public final String myURI;
    public final int myLineNumber;
    public final String myXPath;
    public final String myStringValue;

    public Node(String URI, int lineNumber, String XPath, String stringValue) {
      myURI = URI;
      myLineNumber = lineNumber;
      myXPath = XPath;
      myStringValue = stringValue;
    }

    public String getURI() {
      return myURI;
    }

    public int getLineNumber() {
      return myLineNumber;
    }

    public String toString() {
      return "Node{" +
             "myURI='" + myURI + '\'' +
             ", myLineNumber=" + myLineNumber +
             ", myXPath='" + myXPath + '\'' +
             ", myStringValue='" + myStringValue + '\'' +
             '}';
    }
  }
}
