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
import com.wrq.rearranger.entry.MethodEntry;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.IRule;
import com.wrq.rearranger.settings.attributeGroups.InterfaceAttributes;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/** Handles interface rule processing. */
public class InterfaceRuleInstance
  extends CommonRuleInstance
{
// ------------------------------ FIELDS ------------------------------

  List<InterfaceInstance> interfaceInstances;

// --------------------------- CONSTRUCTORS ---------------------------

  public InterfaceRuleInstance(IRule rule) {
    super(rule);
    interfaceInstances = new ArrayList<InterfaceInstance>();
  }

// -------------------------- OTHER METHODS --------------------------

  /**
   * override the supermethod because we never want to alphabetize methods based on the "alphabetize" flag; that
   * flag controls whether interfaces are alphabetized or not.  Just add the methods to the list in order encountered.
   *
   * @param entry
   */
  public void addEntry(RangeEntry entry) {
    matchedItems.add((MethodEntry)entry);
  }

  public void addRuleInstanceToPopupTree(DefaultMutableTreeNode node, RearrangerSettings settings) {
    /**
     * if we are supposed to show rules, create a node for the rule and put its contents below.
     * Otherwise, delegate to each of the matches.
     */
    DefaultMutableTreeNode top = node;
    if (settings.isShowRules()) {
      top = new DefaultMutableTreeNode(this);
      node.add(top);
    }
    /**
     *  An InterfaceRuleInstance has a list of InterfaceInstances, which in turn list the methods which matched
     * the name of the interface.  Build the tree accordingly.
     */
    for (InterfaceInstance instance : interfaceInstances) {
      instance.addToPopupTree(top, settings);
    }
  }

  public void emit(Emitter emitter) {
    for (Object interfaceInstance : interfaceInstances) {
      InterfaceInstance instance = (InterfaceInstance)interfaceInstance;
      instance.emit(emitter);
    }
  }

  public void rearrangeRuleItems(List<ClassContentsEntry> entries,
                                 RearrangerSettings settings)
  {
    /**
     * create a list of interface instances, one for each interface specified by any of the matched methods.
     * Alphabetize this list if specified.
     */
    for (RangeEntry re : matchedItems) {
      MethodEntry me = (MethodEntry)re;
      InterfaceInstance instance = null;
      for (InterfaceInstance interfaceInstance : interfaceInstances) {
        if (interfaceInstance.getInterfaceName().equals(me.getInterfaceName())) {
          instance = interfaceInstance;
        }
      }
      if (instance == null) {
        final InterfaceAttributes interfaceRule = (InterfaceAttributes)getRule();
        instance = new InterfaceInstance(me.getInterfaceName(), interfaceRule);
        if (interfaceRule.isAlphabetize()) {
          boolean inserted = false;
          String s = instance.getInterfaceName();
          ListIterator<InterfaceInstance> lii = interfaceInstances.listIterator();
          while (lii.hasNext()) {
            InterfaceInstance entry = (lii.next());
            final String ename = entry.getInterfaceName();
            if (s.compareTo(ename) < 0) {
              interfaceInstances.add(lii.nextIndex() - 1, instance);
              inserted = true;
              break;
            }
          }
          if (!inserted) {
            interfaceInstances.add(instance);
          }
        }
        else {
          interfaceInstances.add(instance);
        }
      }
      instance.addMethod(me);
    }
    MethodEntry.rearrangeRelatedItems(entries,
                                      this,
                                      settings.getExtractedMethodsSettings());
  }
}

