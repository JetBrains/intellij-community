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
import com.wrq.rearranger.ruleinstance.RuleInstance;

import java.util.List;

/** Interface which all rules implement. */
public interface Rule {
  RuleInstance createRuleInstance();

  // all rules have a priority; but some are not settable by the user.  Those that are user settable implement
  // the marker interface IPrioritizableRule.
  int getPriority();

  void setPriority(int priority);

  boolean isMatch(RangeEntry rangeEntry);

  /**
   * @param pattern global pattern to which all comments should conform.
   * @return true if all comments associated with this rule match the pattern.
   */
  boolean commentsMatchGlobalPattern(String pattern);

  /**
   * @param pattern global pattern to which all comments should conform.
   * @return list of comments which fail to match the pattern.
   */
  List<String> getOffendingPatterns(String pattern);

  /** @return the number of comments associated with this rule. */
  int getCommentCount();

  /**
   * Calculates a list of regular expression patterns, one for each comment that the rule might create, which
   * will match any comment that the rule might generate.  These can be combined to form a global comment pattern
   * which will match all possible generated comments from all rules.   These patterns will be appended
   * to the supplied list.
   */
  void addCommentPatternsToList(List<String> list);
}
