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
package com.wrq.rearranger;

/** Additional "modifier" flags, used to identify other boolean attributes of a field, method or class. */
public final class ModifierConstants {
// ------------------------------ FIELDS ------------------------------

  /*
  * dummy modifier values -- chosen to not conflict with other Java modifiers
  */
  public static final int OVERRIDDEN         = 0x2000;
  public static final int CONSTRUCTOR        = 0x4000;
  public static final int CANONICAL          = 0x200000;
  public static final int OTHER_METHOD       = 0x10000;
  public static final int OVERRIDING         = 0x20000;
  public static final int IMPLEMENTING       = 0x100000;
  public static final int INIT_TO_ANON_CLASS = 0x40000;
  public static final int INITIALIZER        = 0x80000;
  public static final int IMPLEMENTED        = 0x400000;
  public static final int ENUM               = 0x800000;

  private ModifierConstants() {
  }

  public static String toString(int constants) {
    String result = "";
    if ((constants & OVERRIDDEN) > 0) result += "OVERRIDDEN ";
    if ((constants & CONSTRUCTOR) > 0) result += "CONSTRUCTOR ";
    if ((constants & CANONICAL) > 0) result += "CANONICAL ";
    if ((constants & OTHER_METHOD) > 0) result += "OTHER_METHOD ";
    if ((constants & OVERRIDING) > 0) result += "OVERRIDING ";
    if ((constants & IMPLEMENTED) > 0) result += "IMPLEMENTED ";
    if ((constants & IMPLEMENTING) > 0) result += "IMPLEMENTING ";
    if ((constants & INIT_TO_ANON_CLASS) > 0) result += "INIT_TO_ANON_CLASS ";
    if ((constants & INITIALIZER) > 0) result += "INITIALIZER ";
    if ((constants & ENUM) > 0) result += "ENUM ";
    return result.trim();
  }
}
