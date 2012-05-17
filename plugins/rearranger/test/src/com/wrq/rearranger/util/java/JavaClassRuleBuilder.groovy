package com.wrq.rearranger.util.java

import com.wrq.rearranger.settings.RearrangerSettings
import com.wrq.rearranger.settings.attributeGroups.ClassAttributes
import org.jetbrains.annotations.NotNull

/** 
 * @author Denis Zhdanov
 * @since 5/17/12 11:13 AM
 */
class JavaClassRuleBuilder extends AbstractJavaRuleBuilder<ClassAttributes> {

  @Override
  protected ClassAttributes createRule() {
    new ClassAttributes()
  }

  @Override
  protected void registerRule(@NotNull RearrangerSettings settings, @NotNull ClassAttributes rule) {
    settings.addClass(rule) 
  }
}
