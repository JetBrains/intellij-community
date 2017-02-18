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
import gnu.trove.THashMap;
import org.kohsuke.rngom.digested.*;

import javax.xml.namespace.QName;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 30.07.2007
*/
class AttributeFinder extends RecursionSaveWalker {
  private int depth;
  private int optional;
  private final QName myQname;
  private final Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> myAttributes =
    new THashMap<>();
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
    } finally {
      depth--;
    }
  }

  @Override
  public Void onAttribute(DAttributePattern p) {
    assert depth > 0;

    if (depth == 1 && (myQname == null || p.getName().contains(myQname))) {
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
    } finally {
      optional--;
    }
  }

  @Override
  public Void onZeroOrMore(DZeroOrMorePattern p) {
    optional++;
    try {
      return super.onZeroOrMore(p);
    } finally {
      optional--;
    }
  }

  @Override
  public Void onChoice(DChoicePattern p) {
    optional++;
    try {
      return super.onChoice(p);
    } finally {
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
}
