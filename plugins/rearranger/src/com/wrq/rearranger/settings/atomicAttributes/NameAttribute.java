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

/** Allows item selection by matching its name to a regular expression. */
public final class NameAttribute extends StringAttribute {

// -------------------------- STATIC METHODS --------------------------

  public static NameAttribute readExternal(final Element item) {
    final NameAttribute result = new NameAttribute();
    result.loadAttributes(item.getChild("NameMatch"));
    return result;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public NameAttribute() {
    super("names", "NameMatch");
  }

// ------------------------ CANONICAL METHODS ------------------------

  public final boolean equals(final Object object) {
    if (!(object instanceof NameAttribute)) return false;
    return super.equals(object);
  }

// -------------------------- OTHER METHODS --------------------------

  public final /*NameAttribute*/AtomicAttribute deepCopy() {
    final NameAttribute result = new NameAttribute();
    super.deepCopy(result);
    return result;
  }
}
