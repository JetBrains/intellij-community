// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.index;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class XsdNamespaceBuilder implements Comparable<XsdNamespaceBuilder> {
  public static String computeNamespace(@NotNull InputStream is) {
    return computeNamespace(new InputStreamReader(is, StandardCharsets.UTF_8)).getNamespace();
  }

  public static XsdNamespaceBuilder computeNamespace(@NotNull Reader reader) {
    try (reader) {
      XsdNamespaceBuilder xsdBuilder = new XsdNamespaceBuilder();
      NanoXmlBuilder builder = xsdBuilder.new NanoBuilder();
      NanoXmlUtil.parse(reader, builder);
      HashSet<String> tags = new HashSet<>(xsdBuilder.getTags());
      tags.removeAll(xsdBuilder.myReferencedTags);
      xsdBuilder.getRootTags().addAll(tags);
      return xsdBuilder;
    }
    catch (IOException e) {
      // can never happen
      throw new RuntimeException(e);
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

  private class NanoBuilder implements NanoXmlBuilder {
    @Override
    public void startElement(final @NonNls String name, final @NonNls String nsPrefix, final @NonNls String nsURI, final String systemID, final int lineNr)
        throws Exception {

      if (XmlUtil.XML_SCHEMA_URI.equals(nsURI)) {
        myCurrentTag = name;
      }
      myCurrentDepth++;
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) {
      myCurrentDepth--;
      myCurrentTag = null;
    }

    @Override
    public void addAttribute(final @NonNls String key, final String nsPrefix, final String nsURI, final String value, final String type)
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

    if (!Objects.equals(myNamespace, builder.myNamespace)) return false;
    if (!Objects.equals(myVersion, builder.myVersion)) return false;
    if (!Objects.equals(myTags, builder.myTags)) return false;

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
