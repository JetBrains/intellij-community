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

package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon;

import com.icl.saxon.om.Navigator;
import com.icl.saxon.om.NodeInfo;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 03.06.2007
 */
class SaxonSourceFrame extends AbstractSaxonFrame<Debugger.SourceFrame, NodeInfo> implements Debugger.SourceFrame {
  public SaxonSourceFrame(Debugger.SourceFrame prev, NodeInfo element) {
    super(prev, element);
  }

  public String getXPath() {
    return Navigator.getPath(myElement);
  }
}
