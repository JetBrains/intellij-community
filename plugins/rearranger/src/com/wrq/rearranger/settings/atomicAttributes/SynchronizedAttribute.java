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

import java.lang.reflect.Modifier;

/** Handles the 'synchronized' modifier. */
public final class SynchronizedAttribute
  extends AndNotAttribute
{

// -------------------------- STATIC METHODS --------------------------

  public static SynchronizedAttribute readExternal(final Element item) {
    final SynchronizedAttribute result = new SynchronizedAttribute();
    result.loadAttributes(item.getChild(result.getElementName()));
    return result;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public SynchronizedAttribute() {
    super("synchronized", Modifier.SYNCHRONIZED);
  }

// -------------------------- OTHER METHODS --------------------------

  public final /*SynchronizedAttribute*/AtomicAttribute deepCopy() {
    final SynchronizedAttribute result = new SynchronizedAttribute();
    result.value = value;
    result.invert = invert;
    return result;
  }
}
