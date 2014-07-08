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

import org.jetbrains.idea.svn.api.Depth;

import javax.swing.*;

public class DepthCombo extends JComboBox {
  public DepthCombo(final boolean forUpdate) {
    super(forUpdate ? ourForUpdate : ourForCheckout);
    setSelectedIndex(forUpdate ? 0 : 3);
    setEditable(false);
    setToolTipText(SvnBundle.message("label.depth.description"));
  }

  public Depth getDepth() {
    return ((DepthWithName) super.getSelectedItem()).getDepth();
  }

  private final static DepthWithName [] ourForUpdate = {new DepthWithName(Depth.UNKNOWN, "working copy"),
    new DepthWithName(Depth.EMPTY), new DepthWithName(Depth.FILES), new DepthWithName(Depth.IMMEDIATES),
    new DepthWithName(Depth.INFINITY)};
  private final static DepthWithName [] ourForCheckout = {
    new DepthWithName(Depth.EMPTY), new DepthWithName(Depth.FILES), new DepthWithName(Depth.IMMEDIATES),
    new DepthWithName(Depth.INFINITY)};

  private static class DepthWithName {
    private final Depth myDepth;
    private final String myName;

    private DepthWithName(Depth depth) {
      myDepth = depth;
      myName = myDepth.toString();
    }

    private DepthWithName(Depth depth, String name) {
      myDepth = depth;
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }

    public Depth getDepth() {
      return myDepth;
    }
  }
}
