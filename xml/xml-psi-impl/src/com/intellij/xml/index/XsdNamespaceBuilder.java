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

package com.intellij.xml.index;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class XsdNamespaceBuilder extends NanoXmlUtil.IXMLBuilderAdapter implements Comparable<XsdNamespaceBuilder> {

  public static String computeNamespace(final InputStream is) {
    return computeNamespace(new InputStreamReader(is)).getNamespace();
  }

  public static XsdNamespaceBuilder computeNamespace(final Reader reader) {
    try {
      final XsdNamespaceBuilder builder = new XsdNamespaceBuilder();
      NanoXmlUtil.parse(reader, builder);
      HashSet<String> tags = new HashSet<>(builder.getTags());
      tags.removeAll(builder.myReferencedTags);
      builder.getRootTags().addAll(tags);
      return builder;
    }
    finally {
      try {
        if (reader != null) {
          reader.close();
        }
      }
      catch (IOException e) {
        // can never happen
      }
    }
  }

  private String myCurrentTag;

  private int myCurrentDepth;
  private String myNamespace;

  private String myVersion;
  private final List<String> myTags;
  private final Set<String> myReferencedTags = new HashSet<>();
  private final List<String> myRootTags;
  private final List<String> myAttributes = new ArrayList<>();

  @Override
  public void startElement(@NonNls final String name, @NonNls final String nsPrefix, @NonNls final String nsURI, final String systemID, final int lineNr)
      throws Exception {

    if (XmlUtil.XML_SCHEMA_URI.equals(nsURI)) {
      myCurrentTag = name;
    }
    myCurrentDepth++;
  }

  @Override
  public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
    myCurrentDepth--;
    myCurrentTag = null;
  }

  @Override
  public void addAttribute(@NonNls final String key, final String nsPrefix, final String nsURI, final String value, final String type)
      throws Exception {
    if (myCurrentDepth == 1 && "schema".equals(myCurrentTag)) {
      if ("targetNamespace".equals(key)) {
        myNamespace = value;
      }
      else if ("version".equals(key)) {
        myVersion = value;
      }
    }
    else if ("element".equals(myCurrentTag)) {
      if (myCurrentDepth < 3 && "name".equals(key)) {
        myTags.add(value);
      }
      else if ("ref".equals(key)) {
        myReferencedTags.add(XmlUtil.getLocalName(value).toString());
      }
    }
  }

  @Override
  public int compareTo(@NotNull XsdNamespaceBuilder o) {
    return Comparing.compare(myNamespace, o.myNamespace);
  }

  public boolean hasTag(@NotNull String tagName) {
    return myTags.contains(tagName);
  }

  public int getRating(@Nullable String tagName, @Nullable String version) {
    int rate = 0;
    if (tagName != null && myTags.contains(tagName)) {
      rate |= 0x02;
    }
    if (version != null && version.equals(myVersion)) {
      rate |= 0x01;
    }
    return rate;
  }

  private XsdNamespaceBuilder() {
    myTags = new ArrayList<>();
    myRootTags = new ArrayList<>();
  }

  XsdNamespaceBuilder(String namespace, String version, List<String> tags, List<String> rootTags) {
    myNamespace = namespace;
    myVersion = version;
    myTags = tags;
    myRootTags = rootTags;
  }

  public String getNamespace() {
    return myNamespace;
  }

  public String getVersion() {
    return myVersion;
  }

  public List<String> getTags() {
    return myTags;
  }

  public List<String> getRootTags() {
    return myRootTags;
  }

  public List<String> getAttributes() {
    return myAttributes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    XsdNamespaceBuilder builder = (XsdNamespaceBuilder)o;

    if (myNamespace != null ? !myNamespace.equals(builder.myNamespace) : builder.myNamespace != null) return false;
    if (myVersion != null ? !myVersion.equals(builder.myVersion) : builder.myVersion != null) return false;
    if (myTags != null ? !myTags.equals(builder.myTags) : builder.myTags != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myNamespace != null ? myNamespace.hashCode() : 0;
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myTags != null ? myTags.hashCode() : 0);
    return result;
  }
}
