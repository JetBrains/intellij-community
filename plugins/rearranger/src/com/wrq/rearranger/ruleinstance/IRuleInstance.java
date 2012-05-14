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
package com.wrq.rearranger.ruleinstance;

import com.wrq.rearranger.entry.ClassContentsEntry;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.IRule;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

/**
 * One RuleInstance exists for every execution of every rule.  An execution of a rule is the process by which
 * items are matched to a rule.
 */
public interface IRuleInstance {
  /** @return true if one or more items matched the rule in this instance. */
  boolean hasMatches();

  /** @return list of items matching the rule in this instance. */
  List<RangeEntry> getMatches();

  /**
   * Causes the text representation of this item to be emitted to a buffer, and from there into the final Document.
   *
   * @param emitter Emitter target object with buffer and Document.
   */
  void emit(Emitter emitter);

  /** @return the rule associated with this instance. */
  IRule getRule();

  /**
   * Adds an item to the list of items matching the rule in this instance.  Order is preserved.
   *
   * @param entry matching item
   */
  void addEntry(RangeEntry entry);

  /**
   * Perform any rearranging based on rule or item relationships indicated by the settings.
   *
   * @param entries  list of items (fields, methods, etc.)
   * @param settings
   */
  void rearrangeRuleItems(List<ClassContentsEntry> entries, RearrangerSettings settings);

  /** add the contents of this rule to the file structure popup tree. */
  void addRuleInstanceToPopupTree(DefaultMutableTreeNode node, RearrangerSettings settings);
}
