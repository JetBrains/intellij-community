// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.relaxNG.model.annotation;

import com.intellij.psi.xml.XmlFile;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.intellij.plugins.relaxNG.model.*;

import java.util.List;

final class OverriddenDefineSearcher extends CommonElement.Visitor {
  private final Define<?, ?> myDefine;
  private final IntArrayList myIncludes = new IntArrayList();
  private final XmlFile myLocalFile;
  private final List<? super Define<?, ?>> myResult;

  OverriddenDefineSearcher(Define<?, ?> define, XmlFile localFile, List<? super Define<?, ?>> result) {
    myLocalFile = localFile;
    myResult = result;
    myDefine = define;
  }

  @Override
  public void visitInclude(Include inc) {
    myIncludes.add(inc.getInclude() == myLocalFile ? 1 : 0);
    try {
      inc.acceptChildren(this);
    }
    finally {
      myIncludes.removeInt(myIncludes.size() - 1);
    }
  }

  @Override
  public void visitDiv(Div ref) {
    ref.acceptChildren(this);
  }

  @Override
  public void visitDefine(Define<?, ?> d) {
    if (myIncludes.size() > 0 && myIncludes.getInt(myIncludes.size() - 1) == 1) {
      if (d.getName().equals(myDefine.getName())) {
        myResult.add(d);
      }
    }
    d.acceptChildren(this);
  }

  @Override
  public void visitGrammar(Grammar pattern) {
    pattern.acceptChildren(this);
  }
}
