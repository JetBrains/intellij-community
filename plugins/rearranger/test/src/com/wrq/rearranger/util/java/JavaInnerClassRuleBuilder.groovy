package com.wrq.rearranger.util.java

import com.wrq.rearranger.settings.attributeGroups.InnerClassAttributes
import com.wrq.rearranger.settings.RearrangerSettings

/** 
 * @author Denis Zhdanov
 * @since 5/18/12 8:51 AM
 */
class JavaInnerClassRuleBuilder extends AbstractJavaRuleBuilder<InnerClassAttributes> {

  @Override
  protected InnerClassAttributes createRule() {
    new InnerClassAttributes()
  }

  @Override
  protected void registerRule(RearrangerSettings settings, InnerClassAttributes rule) {
    settings.addItem(rule) 
  }
}
