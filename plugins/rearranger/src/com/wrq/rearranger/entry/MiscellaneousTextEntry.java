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
import com.wrq.rearranger.settings.RearrangerSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/** Corresponds to comments and whitespace that surround Java syntactic items like classes and methods. */
public class MiscellaneousTextEntry extends ClassContentsEntry {
  
  public MiscellaneousTextEntry(final PsiElement start,
                                final PsiElement end,
                                final boolean fixedHeader,
                                final boolean fixedTrailer)
  {
    super(start, end, fixedHeader, fixedTrailer);
  }

  @Nullable
  public String getTypeIconName() {
    // do not show headers and trailers in the file structure popup.
    return null;
  }

  public String[] getAdditionalIconNames() {
    return null;
  }

  @Nullable
  public JLabel getPopupEntryText(RearrangerSettings settings) {
    return null;
  }

  public String toString() {
    String result;
    if (isFixedHeader()) {
      result = "Header";
    }
    else {
      result = "Trailer";
    }
    if (myAlternateValue != null) {
      result += "; comments removed";
    }
    return result;
  }
}
