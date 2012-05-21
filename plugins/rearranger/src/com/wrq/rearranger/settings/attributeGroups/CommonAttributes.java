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

import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.ruleinstance.DefaultRuleInstance;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.atomicAttributes.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Routines for Java modifiers common to fields, methods and classes, namely: protection levels (public, private,
 * protected, package), final modifier, and static modifier.  Ability to match the name to a regular
 * expression is also supported.
 */
public abstract class CommonAttributes implements AttributeGroup, PrioritizedRule {

// ------------------------------ FIELDS ------------------------------

  private ProtectionLevelAttributes myProtectionLevelAttributes;
  private FinalAttribute            myFinalAttribute;
  private StaticAttribute           myStaticAttribute;
  private NameAttribute             myNameAttribute;
  private SortOptions               mySortOptions;
  private int                       myPriority;   // 1 = low priority, > 1 is higher priority

// -------------------------- STATIC METHODS --------------------------

  static void readExternal(final CommonAttributes result, final Element item) {
    result.myProtectionLevelAttributes = ProtectionLevelAttributes.readExternal(item);
    result.myStaticAttribute = StaticAttribute.readExternal(item);
    result.myFinalAttribute = FinalAttribute.readExternal(item);
    result.myNameAttribute = NameAttribute.readExternal(item);
    result.mySortOptions = SortOptions.readExternal(item);
    result.myPriority = RearrangerSettings.getIntAttribute(item, "priority", 1);
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public CommonAttributes() {
    myProtectionLevelAttributes = new ProtectionLevelAttributes();
    myFinalAttribute = new FinalAttribute();
    myStaticAttribute = new StaticAttribute();
    myNameAttribute = new NameAttribute();
    mySortOptions = new SortOptions();
    myPriority = 1;
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  @NotNull
  public final NameAttribute getNameAttribute() {
    return myNameAttribute;
  }

  @NotNull
  final public ProtectionLevelAttributes getProtectionLevelAttributes() {
    return myProtectionLevelAttributes;
  }

  public int getPriority() {
    return myPriority;
  }

  public void setPriority(int priority) {
    this.myPriority = priority;
  }

  @NotNull
  final public StaticAttribute getStaticAttribute() {
    return myStaticAttribute;
  }

  @NotNull
  public SortOptions getSortOptions() {
    return mySortOptions;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public boolean equals(final Object obj) {
    if (!(obj instanceof CommonAttributes)) {
      return false;
    }
    final CommonAttributes ca = (CommonAttributes)obj;
    return myProtectionLevelAttributes.equals(ca.myProtectionLevelAttributes) &&
           myStaticAttribute.equals(ca.myStaticAttribute) &&
           myFinalAttribute.equals(ca.myFinalAttribute) &&
           myNameAttribute.equals(ca.myNameAttribute) &&
           mySortOptions.equals(ca.mySortOptions) &&
           myPriority == ca.myPriority;
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface IRule ---------------------

  @NotNull
  public RuleInstance createRuleInstance() {
    return new DefaultRuleInstance(this);
  }

  public boolean isMatch(@NotNull RangeEntry rangeEntry) {
    return myProtectionLevelAttributes.isMatch(rangeEntry.getModifiers()) && myStaticAttribute.isMatch(rangeEntry.getModifiers())
           && myFinalAttribute.isMatch(rangeEntry.getModifiers()) && myNameAttribute.isMatch(rangeEntry.getName());
  }

  /**
   * Returns a boolean indicating if the comments associated with this rule match a global comment pattern.
   * Most rules don't have comments, so by default it returns true.
   *
   * @param pattern global pattern to which all comments should conform
   * @return true if comments conform to pattern
   */
  public boolean commentsMatchGlobalPattern(String pattern) {
    return true;
  }

  public List<String> getOffendingPatterns(String pattern) {
    return new ArrayList<String>(1);
  }

  public int getCommentCount() {
    return 0;
  }

  public void addCommentPatternsToList(List<String> list) {
  }

// -------------------------- OTHER METHODS --------------------------

  final void deepCopyCommonItems(final CommonAttributes result) {
    result.myProtectionLevelAttributes = (ProtectionLevelAttributes)myProtectionLevelAttributes.deepCopy();
    result.myFinalAttribute = (FinalAttribute)myFinalAttribute.deepCopy();
    result.myStaticAttribute = (StaticAttribute)myStaticAttribute.deepCopy();
    result.myNameAttribute = (NameAttribute)myNameAttribute.deepCopy();
    result.mySortOptions = (SortOptions)mySortOptions.deepCopy();
    result.myPriority = myPriority;
  }

  final public FinalAttribute getFinalAttribute() {
    return myFinalAttribute;
  }

  final void writeExternalCommonAttributes(final Element child) {
    myProtectionLevelAttributes.appendAttributes(child);
    myStaticAttribute.appendAttributes(child);
    myFinalAttribute.appendAttributes(child);
    myNameAttribute.appendAttributes(child);
    mySortOptions.appendAttributes(child);
    child.setAttribute("priority", String.valueOf(myPriority));
  }
}

