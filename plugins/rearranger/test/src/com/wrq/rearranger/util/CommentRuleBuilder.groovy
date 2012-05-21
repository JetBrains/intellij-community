package com.wrq.rearranger.util;

import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RearrangerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 5/17/12 4:08 PM
 */
class CommentRuleBuilder extends AbstractRuleBuilder<CommentRule> {
  
  {
    registerHandler(RearrangerTestDsl.COMMENT, { data, attributes, rule ->
      rule.commentText = data
      setIf(RearrangerTestDsl.CONDITION, attributes, 'emitCondition', rule)
      setIf(RearrangerTestDsl.ALL_SUBSEQUENT, attributes, 'allSubsequentRules', rule)
      setIf(RearrangerTestDsl.ALL_PRECEDING, attributes, 'allPrecedingRules', rule)
      setIf(RearrangerTestDsl.SUBSEQUENT_RULES_TO_MATCH, attributes, 'NSubsequentRulesToMatch', rule)
      setIf(RearrangerTestDsl.PRECEDING_RULES_TO_MATCH, attributes, 'NPrecedingRulesToMatch', rule)
    })
  }
  
  @NotNull
  @Override
  protected CommentRule createRule() {
    new CommentRule()
  }

  @Override
  protected void registerRule(@NotNull RearrangerSettings settings, @NotNull CommentRule rule) {
    settings.addItem(rule) 
  }
}
