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
package org.intellij.plugins.relaxNG.model.annotation;

import com.intellij.psi.xml.XmlFile;
import gnu.trove.TIntArrayList;
import org.intellij.plugins.relaxNG.model.*;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 04.12.2007
* Time: 18:41:39
* To change this template use File | Settings | File Templates.
*/
class OverriddenDefineSearcher extends CommonElement.Visitor {
  private final Define myDefine;
  private final TIntArrayList myIncludes = new TIntArrayList();
  private final XmlFile myLocalFile;
  private final List<Define> myResult;

  public OverriddenDefineSearcher(Define define, XmlFile localFile, List<Define> result) {
    myLocalFile = localFile;
    myResult = result;
    myDefine = define;
  }

  @Override
  public void visitInclude(Include inc) {
    myIncludes.add(inc.getInclude() == myLocalFile ? 1 : 0);
    try {
      inc.acceptChildren(this);
    } finally {
      myIncludes.remove(myIncludes.size() - 1);
    }
  }

  @Override
  public void visitDiv(Div ref) {
    ref.acceptChildren(this);
  }

  @Override
  public void visitDefine(Define d) {
    if (myIncludes.size() > 0 && myIncludes.get(myIncludes.size() - 1) == 1) {
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
