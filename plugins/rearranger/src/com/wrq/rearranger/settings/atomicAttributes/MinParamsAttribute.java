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
package com.wrq.rearranger.settings.atomicAttributes;

import org.jdom.Element;

/** Common routines for 'minimum number of params' modifier. */
public class MinParamsAttribute
  extends IntegerAttribute
{
  private static final String PROPNAME = "MIN_PARAMS";
  // -------------------------- STATIC METHODS --------------------------

  public static MinParamsAttribute readExternal(final Element item) {
    final MinParamsAttribute result = new MinParamsAttribute();
    result.loadAttributes(item.getChild(PROPNAME));
    return result;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public MinParamsAttribute() {
    super("parameter", PROPNAME, IntegerAttribute.OP_GE);
  }

// -------------------------- OTHER METHODS --------------------------

  public final /*AbstractAttribute*/AtomicAttribute deepCopy() {
    final MinParamsAttribute mpa = new MinParamsAttribute();
    mpa.value = value;
    mpa.match = match;
    return mpa;
  }
}
