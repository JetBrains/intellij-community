package com.wrq.rearranger.util.java

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiModifier
import com.wrq.rearranger.util.AbstractRuleBuilder
import com.wrq.rearranger.util.RearrangerTestDsl

/** 
 * @author Denis Zhdanov
 * @since 5/17/12 10:47 AM
 */
abstract class AbstractJavaRuleBuilder<T> extends AbstractRuleBuilder<T> {{
  
  // Name
  registerHandler(RearrangerTestDsl.NAME, { value, attributes, rule ->
    rule.nameAttribute.match = true
    rule.nameAttribute.expression = value
  })
  
  // Sort
  def sortOptions = [
    (SortOption.BY_NAME)               : 'byName',
    (SortOption.BY_TYPE)               : 'byType',
    (SortOption.TYPE_CASE_INSENSITIVE) : 'typeCaseInsensitive',
  ]
  registerHandler(RearrangerTestDsl.SORT, { data, attributes, rule ->
    rule.sortOptions."${sortOptions[data]}" = true
  })
  registerHandler(RearrangerTestDsl.NOT_SORT, { data, attributes, rule ->
    rule.sortOptions."${sortOptions[data]}" = false
  })
  
  // Modifiers
  //   Visibility
  def visibilityHandler = { propertyName, attributes, rule ->
    rule.protectionLevelAttributes."$propertyName" = true
    if (attributes.invert) {
      rule.protectionLevelAttributes.invertProtectionLevel = true
    }
  }
  
  //   Final, static.
  def genericHandlers = [:]
  for (i in [PsiModifier.FINAL, PsiModifier.STATIC]) {
    genericHandlers[i] = createBooleanAttributeHandler("${i}Attribute")
  }
  
  def visibilityModifiers =  [PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL].toSet()
  registerHandler(RearrangerTestDsl.MODIFIER, { value, attributes, rule ->
    if (visibilityModifiers.contains(value)) {
      def propertyName = value == PsiModifier.PACKAGE_LOCAL ? 'plPackage' : "pl${StringUtil.capitalize(value)}"
      visibilityHandler(propertyName, attributes, rule)
    }
    else {
      genericHandlers[value](attributes, rule)
    }
  })
  
  // Priority.
  registerHandler(RearrangerTestDsl.PRIORITY, { value, attributes, rule ->
    rule.priority = value
  })
}}
