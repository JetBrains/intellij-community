package org.intellij.plugins.relaxNG.model.annotation;

import org.intellij.plugins.relaxNG.model.*;

import com.intellij.psi.xml.XmlFile;

import gnu.trove.TIntArrayList;

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

  public void visitInclude(Include inc) {
    myIncludes.add(inc.getInclude() == myLocalFile ? 1 : 0);
    try {
      inc.acceptChildren(this);
    } finally {
      myIncludes.remove(myIncludes.size() - 1);
    }
  }

  public void visitDiv(Div ref) {
    ref.acceptChildren(this);
  }

  public void visitDefine(Define d) {
    if (myIncludes.size() > 0 && myIncludes.get(myIncludes.size() - 1) == 1) {
      if (d.getName().equals(myDefine.getName())) {
        myResult.add(d);
      }
    }
    d.acceptChildren(this);
  }

  public void visitGrammar(Grammar pattern) {
    pattern.acceptChildren(this);
  }
}
