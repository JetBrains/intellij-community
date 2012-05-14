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

import com.intellij.openapi.diagnostic.Logger;
import com.wrq.rearranger.entry.ClassContentsEntry;
import com.wrq.rearranger.entry.ClassEntry;
import com.wrq.rearranger.entry.IRelatableEntry;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.ruleinstance.CommentRuleInstance;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.*;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/** Template for generic rearrangement of items in a class or in a Java file. */
public abstract class GenericRearranger {
  private static final Logger LOG = Logger.getInstance("#" + GenericRearranger.class.getName());
  private final List<AttributeGroup>     rules;
  private final List<ClassContentsEntry> entries;
  private final List<RuleInstance> myResultRuleInstances;
  private final int                      nestingLevel;
  private final RearrangerSettings       settings;

  @SuppressWarnings({"AssignmentToCollectionOrArrayFieldFromParameter"})
  protected GenericRearranger(final List<AttributeGroup> rules,
                              final List<ClassContentsEntry> outerClasses,
                              final int nestingLevel,
                              final RearrangerSettings settings)
  {
    this.rules = rules;
    entries = outerClasses;
    this.nestingLevel = nestingLevel;
    this.settings = settings;
    myResultRuleInstances = new ArrayList<RuleInstance>(rules.size() + 4);
  }

  /**
   * Rearranges the RangeEntry objects contained in entries according to rules specified by the user.
   * Inserts comments as directed.
   *
   * @return rearranged list of RangeEntry and CommentRuleInstance objects.
   */
  public final List<RuleInstance> rearrangeEntries() {
    final List<RuleInstance> prioritizedRuleInstances = new ArrayList<RuleInstance>();
    buildRuleInstanceLists(prioritizedRuleInstances);
    /**
     * recursively reorder contents of every nested ClassEntry.
     */
    for (ClassContentsEntry entry : entries) {
      if (entry instanceof ClassEntry) {
        ((ClassEntry)entry).rearrangeContents();
      }
    }
    matchPrioritizedRules(prioritizedRuleInstances);

    /**
     * Move related methods together.  Extracted methods and setters (emitted with getters)
     * were not moved by the rearrangement code ("MatchPrioritizedRules()") just above.
     */
    rearrangeRelatedItems(entries, myResultRuleInstances);
    /**
     * Now go back and determine which comments are going to be emitted, based on their criteria and the
     * state of the immediately surrounding rules.  Ignore inner classes if no rearrangement of inner
     * classes is taking place.
     */
    if (nestingLevel <= 1 || settings.isRearrangeInnerClasses()) {
      determineEmittedComments();
    }
    return myResultRuleInstances;
  }

  /** For each comment rule instance, test its "emit" condition and set its emit flag appropriately. */
  private void determineEmittedComments() {
    for (int i = 0; i < myResultRuleInstances.size(); i++) {
      if (myResultRuleInstances.get(i) instanceof CommentRuleInstance) {
        final CommentRuleInstance ce = (CommentRuleInstance)myResultRuleInstances.get(i);
        ce.determineEmit(myResultRuleInstances, i);
        if (ce.isEmit()) {
          // set the "separator comment preceding" flag on the next matching RangeEntry.
          for (int j = i + 1; j < myResultRuleInstances.size(); j++) {
            RuleInstance instance = myResultRuleInstances.get(j);
            if (instance.hasMatches()) {
              RangeEntry entry = (instance.getMatches().get(0));
              entry.setSeparatorCommentPrecedes(true);
              break;
            }
          }
        }
      }
    }
  }

  /**
   * For each rule (in prioritized order), test all remaining entries to identify those that match.
   * Exclude any extracted (i.e. related) methods and any setter methods that will be emitted with
   * a corresponding getter.  If an entry matches the rule, add it to the list of entries matching the
   * rule and remove it from the list of remaining entries.
   *
   * @param prioritizedRules prioritized list of rules
   */
  @SuppressWarnings({"StringContatenationInLoop"})
  private void matchPrioritizedRules(List<RuleInstance> prioritizedRules) {
    for (RuleInstance ruleInstance : prioritizedRules) {
      final Rule rule = ruleInstance.getRule();
      final ListIterator entryIterator = entries.listIterator();
      while (entryIterator.hasNext()) {
        final RangeEntry entry = (RangeEntry)entryIterator.next();
        if (entry instanceof IRelatableEntry) {
          // if this is an extracted (i.e. related) method, or
          // if this is a setter that will be emitted under the corresponding getter,
          // don't test it for match against the rule.
          final IRelatableEntry relatableEntry = ((IRelatableEntry)entry);
          if (relatableEntry.isRelatedMethod() || relatableEntry.isEmittableSetter()) {
            continue;
          }
        }
        if (rule.isMatch(entry)) {
          LOG.debug("rule:" + rule.toString() + "; entry matched:" + entry.getEnd().toString());
          ruleInstance.addEntry(entry);
          entry.setMatchedRule(ruleInstance);
          entryIterator.remove();
        }
      }
    }
  }

  /**
   * Build two rule instance lists, one in order of emission ("resultRuleInstances") and one
   * in order of matching ("prioritizedRuleInstances").
   *
   * @param prioritizedRuleInstances
   */
  private void buildRuleInstanceLists(final List<RuleInstance> prioritizedRuleInstances) {
    /**
     * add a HeaderTrailerRuleInstance to pick up any headers that might exist.
     */
    RuleInstance hri = new HeaderRule().createRuleInstance();
    myResultRuleInstances.add(hri);
    prioritizedRuleInstances.add(hri);
    for (Rule rule : rules) {
      RuleInstance instance = rule.createRuleInstance();
      myResultRuleInstances.add(instance);
      /**
       * now insert the rule instance into the prioritized list; highest priority first; stable insertion.
       */
      boolean inserted = false;
      for (int i = prioritizedRuleInstances.size() - 1; i >= 0; i--) {
        RuleInstance entry = (prioritizedRuleInstances.get(i));
        if (rule.getPriority() <= entry.getRule().getPriority()) {
          prioritizedRuleInstances.add(i + 1, instance);
          inserted = true;
          break;
        }
      }
      if (!inserted) {
        prioritizedRuleInstances.add(0, instance);
      }
    }
    // now add a default rule to pick up all unmatched items.
    RuleInstance defaultRuleInstance = new DefaultRule().createRuleInstance();
    myResultRuleInstances.add(defaultRuleInstance);
    prioritizedRuleInstances.add(defaultRuleInstance);
    // finally, add a TrailerRuleInstance to pick up any leftover text.
    RuleInstance tri = new TrailerRule().createRuleInstance();
    myResultRuleInstances.add(tri);
    prioritizedRuleInstances.add(tri);
  }

  /**
   * Move any related items (e.g., extracted methods) remaining in 'entries' into the correct position in
   * rearrangedEntries.
   *
   * @param entries
   * @param rearrangedEntries
   */
  public abstract void rearrangeRelatedItems(List<ClassContentsEntry> entries,
                                             List<RuleInstance> rearrangedEntries);
}

