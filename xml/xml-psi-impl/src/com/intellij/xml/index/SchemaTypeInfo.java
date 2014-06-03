/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xml.index;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/4/12
 * Time: 7:14 PM
 */
public class SchemaTypeInfo implements Comparable<SchemaTypeInfo> {
  private final String myTagName;
  private final String myNamespaceUri;
//  private final String myFileUrl;
  private final boolean myIsTypeName; // false -> enclosing element name

  public SchemaTypeInfo(String tagName, final boolean isTypeName, String namespace) {
    myNamespaceUri = namespace;
    myTagName = tagName;
    myIsTypeName = isTypeName;
  }

  public String getTagName() {
    return myTagName;
  }

  public String getNamespaceUri() {
    return myNamespaceUri;
  }

  public boolean isIsTypeName() {
    return myIsTypeName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SchemaTypeInfo info = (SchemaTypeInfo)o;

    if (myIsTypeName != info.myIsTypeName) return false;
    if (myNamespaceUri != null ? !myNamespaceUri.equals(info.myNamespaceUri) : info.myNamespaceUri != null) return false;
    if (!myTagName.equals(info.myTagName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myTagName.hashCode();
    result = 31 * result + (myNamespaceUri != null ? myNamespaceUri.hashCode() : 0);
    result = 31 * result + (myIsTypeName ? 1 : 0);
    return result;
  }

  @Override
  public int compareTo(SchemaTypeInfo o) {
    return myTagName.compareTo(o.getTagName());
  }
}
