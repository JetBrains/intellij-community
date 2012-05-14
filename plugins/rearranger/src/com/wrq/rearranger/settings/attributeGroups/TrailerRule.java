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
import com.wrq.rearranger.ruleinstance.HeaderTrailerRuleInstance;
import com.wrq.rearranger.ruleinstance.RuleInstance;

import java.util.ArrayList;
import java.util.List;

/** A rule to match header text (text which does not constitute a matchable item such as a field or method.) */
public class TrailerRule
  implements Rule
{
  public RuleInstance createRuleInstance() {
    return new HeaderTrailerRuleInstance(this);
  }

  public int getPriority() {
    return -1; // priority doesn't matter since header rules only match the one trailer entry (if it exists), and
    // the trailer entry is never matched by another rule.
  }

  public void setPriority(int priority) {
    // does nothing
  }

  public boolean isMatch(RangeEntry rangeEntry) {
    return rangeEntry.isFixedTrailer();
  }

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
}
