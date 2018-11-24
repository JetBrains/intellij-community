/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.util.Navigator;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;

class Saxon9SourceFrame<N extends NodeInfo> extends AbstractSaxon9Frame<Debugger.SourceFrame, N> implements Debugger.SourceFrame {

  protected Saxon9SourceFrame(Debugger.SourceFrame prev, N element) {
    super(prev, element);
  }


  public String getXPath() {
    return Navigator.getPath(myElement);
  }
}
