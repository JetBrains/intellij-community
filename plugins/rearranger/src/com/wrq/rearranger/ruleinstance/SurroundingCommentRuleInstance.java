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

import com.wrq.rearranger.settings.CommentRule;

import java.util.List;

/** Used to store a generated comment which is emitted based on whether it matches surrounding rules. */
public class SurroundingCommentRuleInstance
  extends CommentRuleInstance
{

  public SurroundingCommentRuleInstance(final CommentRule commentRule) {
    super(commentRule);
  }

  /** Determine if this comment, in this instance, should be emitted. */
  public void determineEmit(List<RuleInstance> resultRuleInstances, int startIndex) {
    if (match(
      resultRuleInstances,
      myCommentRule.getNPrecedingRulesToMatch(),
      -1,
      startIndex,
      myCommentRule.isAllPrecedingRules()
    ) &&
        match(
          resultRuleInstances,
          myCommentRule.getNSubsequentRulesToMatch(),
          +1,
          startIndex,
          myCommentRule.isAllSubsequentRules()
        ))
    {
      setEmit(true);
    }
  }
}