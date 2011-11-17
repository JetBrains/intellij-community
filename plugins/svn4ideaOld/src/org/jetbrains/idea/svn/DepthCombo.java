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
package org.jetbrains.idea.svn;

import org.tmatesoft.svn.core.SVNDepth;

import javax.swing.*;

public class DepthCombo extends JComboBox {
  public DepthCombo(final boolean forUpdate) {
    super(forUpdate ? ourForUpdate : ourForCheckout);
    setSelectedIndex(forUpdate ? 0 : 3);
    setEditable(false);
    setToolTipText(SvnBundle.message("label.depth.description"));
  }

  public SVNDepth getDepth() {
    return ((SVNDepthWithName) super.getSelectedItem()).getDepth();
  }

  private final static SVNDepthWithName [] ourForUpdate = {new SVNDepthWithName(SVNDepth.UNKNOWN, "working copy"),
    new SVNDepthWithName(SVNDepth.EMPTY), new SVNDepthWithName(SVNDepth.FILES), new SVNDepthWithName(SVNDepth.IMMEDIATES),
    new SVNDepthWithName(SVNDepth.INFINITY)};
  private final static SVNDepthWithName [] ourForCheckout = {
    new SVNDepthWithName(SVNDepth.EMPTY), new SVNDepthWithName(SVNDepth.FILES), new SVNDepthWithName(SVNDepth.IMMEDIATES),
    new SVNDepthWithName(SVNDepth.INFINITY)};

  private static class SVNDepthWithName {
    private final SVNDepth myDepth;
    private final String myName;

    private SVNDepthWithName(SVNDepth depth) {
      myDepth = depth;
      myName = myDepth.toString();
    }

    private SVNDepthWithName(SVNDepth depth, String name) {
      myDepth = depth;
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }

    public SVNDepth getDepth() {
      return myDepth;
    }
  }
}
