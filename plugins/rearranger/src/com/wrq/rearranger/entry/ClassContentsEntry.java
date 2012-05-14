/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.entry;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.wrq.rearranger.popup.IFilePopupEntry;
import com.wrq.rearranger.popup.RearrangerTreeNode;
import com.wrq.rearranger.settings.RearrangerSettings;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Describes the fields, methods, and inner class declarations of a class.  This information is used to reorder
 * these declarations within an outer class.
 */
public abstract class ClassContentsEntry
  extends RangeEntry
  implements IFilePopupEntry
{
  public ClassContentsEntry(final PsiElement start,
                            final PsiElement end,
                            final int modifiers,
                            final String modifierString,
                            final String name,
                            final String type)
  {
    super(start, end, modifiers, modifierString, name, type);
  }

  public ClassContentsEntry(final PsiElement start,
                            final PsiElement end,
                            final boolean fixedHeader,
                            final boolean fixedTrailer)
  {
    super(start, end, fixedHeader, fixedTrailer);
  }

  public DefaultMutableTreeNode addToPopupTree(DefaultMutableTreeNode parent, RearrangerSettings settings) {
    DefaultMutableTreeNode node = null;
    if ((end instanceof PsiField && settings.isShowFields()) ||
        (this instanceof ClassEntry))
    {
      node = new RearrangerTreeNode(this, name);
      parent.add(node);
    }
    return node;
  }
}
