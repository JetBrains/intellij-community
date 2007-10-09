/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.util.xmlb;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;


class XIncludeNodeList implements NodeList {

  private List<Node> data = new ArrayList<Node>();

  // could easily expose more List methods if they seem useful
  public void add(int index, Node node) {
    data.add(index, node);
  }

  public void add(Node node) {
    data.add(node);
  }

  public void add(NodeList nodes) {
    for (int i = 0; i < nodes.getLength(); i++) {
      data.add(nodes.item(i));
    }
  }

  public Node item(int index) {
    return data.get(index);
  }

  // copy DOM JavaDoc
  public int getLength() {
    return data.size();
  }

}


class XIncludeException extends RuntimeException {

  public XIncludeException() {
  }

  public XIncludeException(final String message) {
    super(message);
  }

  public XIncludeException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public XIncludeException(final Throwable cause) {
    super(cause);
  }
}