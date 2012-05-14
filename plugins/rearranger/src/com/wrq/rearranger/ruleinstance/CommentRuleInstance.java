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
import com.wrq.rearranger.popup.FilePopupEntry;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.Rule;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Used to store a generated comment, and to determine if it should be emitted. */
public abstract class CommentRuleInstance
  implements RuleInstance,
             FilePopupEntry
{
  protected final CommentRule commentRule;
  protected       boolean     emit;

  public CommentRuleInstance(final CommentRule commentRule) {
    this.commentRule = commentRule;
    emit = false;
  }

  public boolean isEmit() {
    return emit;
  }

  public void setEmit(final boolean emit) {
    this.emit = emit;
  }

  public Rule getRule() {
    return commentRule;
  }

  /** Determine if this comment, in this instance, should be emitted. */
  public abstract void determineEmit(List<RuleInstance> resultRuleInstances, int startIndex);

  public boolean hasMatches() {
    return false; // a generated comment never matches any object
  }

  public void emit(Emitter emitter) {
    StringBuffer sb = emitter.getStringBuffer();
    if (emit) {
      // emit a comment.  Precede with a newline unless this is the first line of the file.
      if (sb.length() > 0) {
        sb.append('\n');
        sb.append(commentRule.getExpandedCommentText());
      }
      else {
        sb.append(commentRule.getExpandedCommentText());
        sb.append('\n');
      }
    }
  }

  public void addEntry(RangeEntry entry) // adds entry to list of matching entries, in correct order
  {
    // comment rules don't match anything in the existing file; they conditionally generate comments.
    // So this method will never be called.
    throw new RuntimeException("cannot add entry to comment rule instance");
  }

  public List<RangeEntry> getMatches() {
    return new ArrayList<RangeEntry>();
  }

  public void rearrangeRuleItems(List<ClassContentsEntry> entries,
                                 RearrangerSettings settings)
  {
  }

  public void addRuleInstanceToPopupTree(DefaultMutableTreeNode node, RearrangerSettings settings) {
    /**
     * if we are supposed to show comment rules, create a node for the rule and put its contents below.
     * Otherwise, just add comments.
     */
    if (!settings.isShowComments()) return;
    DefaultMutableTreeNode top = node;
    if (settings.isShowRules()) {
      // if "show matched rules only" is set, only display if comment will be emitted
      if ((!settings.isShowMatchedRules()) || isEmit()) {
        top = new DefaultMutableTreeNode(this);
        node.add(top);
      }
    }
    /**
     * append the comment if generated and if showComments is set.
     */
    if (isEmit()) {
      commentRule.addToPopupTree(top, settings);
    }
  }

  public String getTypeIconName() {
    return null;
  }

  @Nullable
  public String[] getAdditionalIconNames() {
    return null;
  }

  public JLabel getPopupEntryText(RearrangerSettings settings) {
    JLabel label = new JLabel(commentRule.toString());
    Font font = label.getFont().deriveFont(Font.ITALIC);
    label.setFont(font);
    return label;
  }

  public String toString() {
    return "instance of:" + commentRule.toString();
  }

  /**
   * beginning at the index of ruleStatistics (which is a comment entry), find nRules in the direction indicated,
   * and test to see if all or any of them had entries that matched.
   *
   * @param nRules     number of rules to consider for matching algorithm.
   * @param direction  1 = subsequent, -1 = previous.
   * @param startIndex comment entry index, sandwiched between rules.
   * @param ANDing     if true, all rules must be matched; if false, any rule that matches returns a result of true.
   * @return true if the match condition is met.
   */
  @SuppressWarnings({"MethodParameterNamingConvention"})
  protected boolean match(final List<RuleInstance> resultRuleInstances,
                          final int nRules,
                          final int direction,
                          final int startIndex,
                          final boolean ANDing)
  {
    int index = startIndex + direction;
    int nRulesSeen = 0;
    while (index >= 0 &&
           index < resultRuleInstances.size() &&
           nRulesSeen < nRules)
    {
      if (!(resultRuleInstances.get(index) instanceof CommentRuleInstance)) {
        // this is a rule entry, not a comment.
        nRulesSeen++;
        final RuleInstance ruleInstance = resultRuleInstances.get(index);
        if (ruleInstance.hasMatches()) {
          if (!ANDing) {
            return true; // OR condition (any matching rule) met
          }
        }
        else {
          if (ANDing) {
            return false; // AND condition not met
          }
        }
      }
      index += direction;
    }
    return ANDing;
  }
}

