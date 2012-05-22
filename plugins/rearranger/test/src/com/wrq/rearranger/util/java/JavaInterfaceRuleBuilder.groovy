package com.wrq.rearranger.util.java

import com.wrq.rearranger.settings.attributeGroups.InterfaceAttributes
import com.wrq.rearranger.settings.RearrangerSettings
import com.wrq.rearranger.util.RearrangerTestDsl
import com.wrq.rearranger.settings.CommentRule
import com.wrq.rearranger.util.RearrangerTestUtil

/** 
 * @author Denis Zhdanov
 * @since 5/22/12 11:56 AM
 */
class JavaInterfaceRuleBuilder extends AbstractJavaRuleBuilder<InterfaceAttributes> {
  
  {
    def commentHandler = { rule, commentText, propertyName ->
      def comment = new CommentRule()
      comment.commentText = commentText
      rule."$propertyName" = comment
    }
    
    def nameHandler = createStringAttributeHandler('nameAttr')
    
    registerHandler(RearrangerTestDsl.PRECEDING_COMMENT, { value, attributes, rule -> commentHandler(rule, value, 'precedingComment') })
    registerHandler(RearrangerTestDsl.TRAILING_COMMENT, { value, attributes, rule -> commentHandler(rule, value, 'trailingComment') })
    registerHandler(RearrangerTestDsl.SETUP, { value, attributes, rule -> 
      RearrangerTestUtil.setIf(RearrangerTestDsl.GROUP_EXTRACTED_METHODS, attributes, 'noExtractedMethods', rule)
      RearrangerTestUtil.setIf(RearrangerTestDsl.ORDER, attributes, 'methodOrder', rule)
      RearrangerTestUtil.setIf(RearrangerTestDsl.ALPHABETIZE, attributes, 'alphabetizeInterfaces', rule)
      if (attributes[RearrangerTestDsl.NAME.value]) {
        nameHandler(attributes[RearrangerTestDsl.NAME.value], attributes, rule)
      }
    })
  }
  
  @Override
  protected InterfaceAttributes createRule() {
    new InterfaceAttributes()
  }

  @Override
  protected void registerRule(RearrangerSettings settings, InterfaceAttributes rule) {
    settings.addItem(rule) 
  }
}
