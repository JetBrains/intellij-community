/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.model.descriptors;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.rngom.digested.*;
import org.kohsuke.rngom.nc.NameClass;

import javax.xml.namespace.QName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class AttributeFinder extends RecursionSaveWalker {

  private static final String STAR_PATTERN_SUFFIX = "__star__";

  private int depth;
  private int optional;
  private final QName myQname;
  private final Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> myAttributes = new LinkedHashMap<>();
  private DAttributePattern myLastAttr;

  private AttributeFinder() {
    myQname = null;
    optional++; // everything will be optional
  }

  private AttributeFinder(QName qname) {
    myQname = qname;
  }

  @Override
  public Void onElement(DElementPattern p) {
    depth++;
    try {
      myLastAttr = null;
      if (depth < 2) {
        return super.onElement(p);
      }
      return null;
    }
    finally {
      depth--;
    }
  }

  @Override
  public Void onAttribute(DAttributePattern p) {
    assert depth > 0;

    if (depth == 1 && ((myQname == null && !hasStarPattern(p.getName()))
                       || (myQname != null && (p.getName().contains(myQname) || hasStarMatch(p.getName(), myQname))))) {
      myLastAttr = p;
      if (!myAttributes.containsKey(p)) {
        myAttributes.put(p, Pair.create(new LinkedHashMap<>(), optional > 0));
      }
      return super.onAttribute(p);
    }
    return null;
  }

  @Override
  public Void onValue(DValuePattern p) {
    if (myLastAttr != null) {
      myAttributes.get(myLastAttr).first.put(p.getValue(), p.getType());
    }
    return super.onValue(p);
  }

  @Override
  public Void onOptional(DOptionalPattern p) {
    optional++;
    try {
      return super.onOptional(p);
    }
    finally {
      optional--;
    }
  }

  @Override
  public Void onZeroOrMore(DZeroOrMorePattern p) {
    optional++;
    try {
      return super.onZeroOrMore(p);
    }
    finally {
      optional--;
    }
  }

  @Override
  public Void onChoice(DChoicePattern p) {
    optional++;
    try {
      return super.onChoice(p);
    }
    finally {
      optional--;
    }
  }

  @Override
  public Void onData(DDataPattern p) {
    if (depth == 1 && myLastAttr != null) {
      myAttributes.get(myLastAttr).first.put(null, p.getType());
    }
    return null;
  }

  public static Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> find(QName qname, DPattern... patterns) {
    final AttributeFinder finder = new AttributeFinder(qname);
    finder.doAccept(patterns);
    return finder.myAttributes;
  }

  public static Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> find(DPattern... patterns) {
    final AttributeFinder finder = new AttributeFinder();
    finder.doAccept(patterns);
    return finder.myAttributes;
  }

  private static boolean hasStarPattern(@NotNull NameClass patternClass) {
    return !patternClass.isOpen() && ContainerUtil.find(patternClass.listNames(), pattern ->
      pattern.getLocalPart().endsWith(STAR_PATTERN_SUFFIX)) != null;
  }

  private static boolean hasStarMatch(@NotNull NameClass patternClass, @NotNull QName qname) {
    return !patternClass.isOpen() && ContainerUtil.find(patternClass.listNames(), pattern -> {
      String patternLocal = pattern.getLocalPart();
      if (patternLocal.endsWith(STAR_PATTERN_SUFFIX)
          && Objects.equals(qname.getNamespaceURI(), pattern.getNamespaceURI())) {
        String prefixPattern = patternLocal.substring(0, patternLocal.length() - STAR_PATTERN_SUFFIX.length());
        String name = qname.getLocalPart();
        return name.length() > prefixPattern.length() && name.startsWith(prefixPattern);
      }
      return false;
    }) != null;
  }
}
