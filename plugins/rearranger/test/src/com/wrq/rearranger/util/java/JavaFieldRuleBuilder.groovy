package com.wrq.rearranger.util.java

import com.wrq.rearranger.settings.RearrangerSettings
import org.jetbrains.annotations.NotNull
import com.wrq.rearranger.settings.attributeGroups.FieldAttributes
import com.wrq.rearranger.util.RearrangerTestDsl

/** 
 * @author Denis Zhdanov
 * @since 5/17/12 10:49 AM
 */
class JavaFieldRuleBuilder extends AbstractJavaRuleBuilder<FieldAttributes> {
  
  {
    def handlers = [(InitializerType.ANONYMOUS_CLASS) : createBooleanAttributeHandler('initialisedByAnonymousClassAttr')]
    registerHandler(RearrangerTestDsl.INITIALIZER, { data, attributes, rule -> handlers[data](attributes, rule) })
    registerHandler(RearrangerTestDsl.TYPE, createStringAttributeHandler('typeAttr'))
  }
  
  @Override
  protected FieldAttributes createRule() {
    new FieldAttributes()
  }

  @Override
  protected void registerRule(@NotNull RearrangerSettings settings, @NotNull FieldAttributes rule) {
    settings.addItem(rule)
  }
}
