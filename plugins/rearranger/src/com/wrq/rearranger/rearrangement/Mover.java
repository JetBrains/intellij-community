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
package com.wrq.rearranger.rearrangement;

import com.wrq.rearranger.entry.ClassContentsEntry;
import com.wrq.rearranger.ruleinstance.IRuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;

import java.util.ArrayList;
import java.util.List;

/** Moves (rearranges) classes and class members according to rules specified by the user. */
public final class Mover {
  private final List<ClassContentsEntry> outerClasses;
  private final RearrangerSettings       settings;

  public Mover(final List<ClassContentsEntry> outerClasses,
               final RearrangerSettings settings)
  {
    this.outerClasses = new ArrayList<ClassContentsEntry>(outerClasses);
    this.settings = settings;
  }

  public List<IRuleInstance> rearrangeOuterClasses() {
    final GenericRearranger outerClassRearranger = new GenericRearranger(
      settings.getClassOrderAttributeList(),
      outerClasses,
      0,
      settings)
    {
      /**
       * There are no items related to outer classes -- outer classes are completely disjoint
       * and independent.  Hence this routine does nothing.
       * @param entries


       @param rearrangedEntries
       */
      public void rearrangeRelatedItems(List<ClassContentsEntry> entries,
                                        List<IRuleInstance> rearrangedEntries)
      {
      }
    };

    return outerClassRearranger.rearrangeEntries();
  }
}