package com.wrq.rearranger.util.java

import com.wrq.rearranger.settings.RearrangerSettings
import com.wrq.rearranger.settings.attributeGroups.MethodAttributes
import org.jetbrains.annotations.NotNull
import com.wrq.rearranger.util.RearrangerTestDsl

/** 
 * @author Denis Zhdanov
 * @since 5/17/12 11:01 AM
 */
class JavaMethodRuleBuilder extends AbstractJavaRuleBuilder<MethodAttributes> {
  
  {
    def handlers = [
      (MethodType.CONSTRUCTOR) : createRawBooleanAttributeHandler('constructorMethodType'),
      (MethodType.GETTER_OR_SETTER) : createRawBooleanAttributeHandler('getterSetterMethodType'),
      (MethodType.OTHER) : createRawBooleanAttributeHandler('otherMethodType')
    ]
    registerHandler(RearrangerTestDsl.TARGET, { data, attributes, rule -> handlers[data](attributes, rule) })
    registerHandler(RearrangerTestDsl.RETURN_TYPE, createStringAttributeHandler('returnTypeAttr'))
  }
  
  @Override
  protected MethodAttributes createRule() {
    new MethodAttributes()
  }

  @Override
  protected void registerRule(@NotNull RearrangerSettings settings, @NotNull MethodAttributes rule) {
    settings.addItem(rule)
  }
}
