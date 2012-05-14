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
package com.wrq.rearranger.settings;

import org.jdom.Element;

/** Contains a single "force N blank lines before/after" some brace. */
public class ForceBlankLineSetting {
  public static final int CLASS_OBJECT  = 0;
  public static final int METHOD_OBJECT = 1;
  public static final int EOF_OBJECT    = 2;

  boolean force;
  int     nBlankLines;
  final boolean before;     // false => after
  final boolean openBrace;  // false => close brace
  final int     object;
  final String  name;

  public ForceBlankLineSetting(boolean before, boolean openBrace, int object, String name) {
    this.before = before;
    this.openBrace = openBrace;
    this.object = object;
    this.name = name;
  }

  public boolean isForce() {
    return force;
  }

  public void setForce(boolean force) {
    this.force = force;
  }

  public int getnBlankLines() {
    return nBlankLines;
  }

  public void setnBlankLines(int nBlankLines) {
    this.nBlankLines = nBlankLines;
  }

  public boolean isBefore() {
    return before;
  }

  public boolean isOpenBrace() {
    return openBrace;
  }

  public int getObject() {
    return object;
  }


  public boolean equals(final Object obj) {
    if (!(obj instanceof ForceBlankLineSetting)) return false;
    final ForceBlankLineSetting fbls = (ForceBlankLineSetting)obj;
    if (fbls.force != force) return false;
    if (fbls.nBlankLines != nBlankLines) return false;
    if (fbls.before != before) return false;
    if (fbls.openBrace != openBrace) return false;
    if (fbls.object != object) return false;
    return true;
  }

  public final ForceBlankLineSetting deepCopy() {
    final ForceBlankLineSetting result = new ForceBlankLineSetting(before, openBrace, object, name);
    result.force = force;
    result.nBlankLines = nBlankLines;
    return result;
  }

  /**
   * Read the contents of this object from the JDOM element.
   *
   * @param entry JDOM element which contains setting values as attributes.
   */
  public static ForceBlankLineSetting readExternal(final Element entry,
                                                   boolean before,
                                                   boolean openBrace,
                                                   int object,
                                                   String name)
  {
    ForceBlankLineSetting fbls;
    Element fblsElement = entry.getChild(name);
    boolean force = RearrangerSettings.getBooleanAttribute(fblsElement, "Force", false);
    int nBlankLines = RearrangerSettings.getIntAttribute(fblsElement, "nBlankLines", 1);
    fbls = new ForceBlankLineSetting(before, openBrace, object, name);
    fbls.setForce(force);
    fbls.setnBlankLines(nBlankLines);
    return fbls;
  }

  public final void writeExternal(final Element entry) {
    final Element fblsElement = new Element(name);
    entry.getChildren().add(fblsElement);
    fblsElement.setAttribute("Force", Boolean.valueOf(force).toString());
    fblsElement.setAttribute("nBlankLines", "" + nBlankLines);
  }

  public String toString() {
    return name + ": " + (before ? "before " : "after ") +
           getObjectName() +
           (openBrace ? " open brace" : " close brace") +
           (force ? ", force " + nBlankLines + " lines" : ", leave intact");
  }

  public String getObjectName() {
    return (object == CLASS_OBJECT ? "class" : "method");
  }
}
