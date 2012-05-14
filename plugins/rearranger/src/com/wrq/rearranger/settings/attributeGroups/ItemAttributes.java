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
package com.wrq.rearranger.settings.attributeGroups;

import com.wrq.rearranger.settings.CommentRule;
import org.jdom.Element;

/** Routines to handle attributes common to class members (fields, methods, and inner classes.) */
abstract public class ItemAttributes
  extends CommonAttributes
{

// -------------------------- STATIC METHODS --------------------------

  public static AttributeGroup readExternal(final Element element) {
    if (element.getName().equals("Field")) {
      return FieldAttributes.readExternal(element);
    }
    else if (element.getName().equals("Method")) {
      return MethodAttributes.readExternal(element);
    }
    else if (element.getName().equals("InnerClass")) {
      return InnerClassAttributes.readExternal(element);
    }
    else if (element.getName().equals("Interface")) {
      return InterfaceAttributes.readExternal(element);
    }
    else if (element.getName().equals("Comment")) {
      return CommentRule.readExternal(element);
    }
    else {
      return null;
    }
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface AttributeGroup ---------------------

  abstract public /*ItemAttributes*/AttributeGroup deepCopy();
}

