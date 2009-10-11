/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class JDOMReader {
  private final Element myRootElement;
  private final Namespace myNamespace;

  public JDOMReader(InputStream s) throws IOException {
    try {
      Document document = new SAXBuilder().build(s);
      if (!document.hasRootElement()) {
        throw new IOException("root element not found");
      }
      myRootElement = document.getRootElement();
      myNamespace = myRootElement.getNamespace();
    }
    catch (JDOMException e) {
      IOException ioException = new IOException();
      ioException.initCause(e);
      throw ioException;
    }
  }

  public Element getRootElement() {
    return myRootElement;
  }

  public Element getChild(Element element, @NonNls String tag) {
    return element.getChild(tag, myNamespace);
  }

  public List<Element> getChildren(Element element, @NonNls String tag) {
    return element.getChildren(tag, myNamespace);
  }

  public String getChildText(Element element, @NonNls String tag) {
    return element.getChildText(tag, myNamespace);
  }
}
