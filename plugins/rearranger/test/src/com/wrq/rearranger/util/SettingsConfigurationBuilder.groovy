package com.wrq.rearranger.util

import com.wrq.rearranger.settings.RearrangerSettings

import static com.wrq.rearranger.util.RearrangerTestUtil.setIf

/**
 * @author Denis Zhdanov
 * @since 5/22/12 11:07 AM
 */
class SettingsConfigurationBuilder extends BuilderSupport {
  
  def RearrangerSettings settings
  
  @Override
  protected void setParent(Object parent, Object child) {
  }

  @Override
  protected Object createNode(Object name) {
    createNode(name, [:], [])
  }

  @Override
  protected Object createNode(Object name, Object value) {
    createNode(name, [:], value)
  }

  @Override
  protected Object createNode(Object name, Map attributes) {
    createNode(name, attributes, [])
  }

  @Override
  protected Object createNode(Object name, Map attributes, Object value) {
    switch (name) {
      case RearrangerTestDsl.EXTRACTED_METHODS.value:
        settings.extractedMethodsSettings.moveExtractedMethods = true
        setIf(RearrangerTestDsl.DEPTH_FIRST_ORDER, attributes, 'depthFirstOrdering', settings.extractedMethodsSettings)
        setIf(RearrangerTestDsl.ORDER, attributes, 'ordering', settings.extractedMethodsSettings)
        setIf(RearrangerTestDsl.COMMENT_TYPE, attributes, 'commentType', settings.extractedMethodsSettings)
        break
      case RearrangerTestDsl.KEEP_TOGETHER.value:
        def m = [
          (RearrangerTestDsl.OVERLOADED.value)                    : 'keepOverloadedMethodsTogether',
          (RearrangerTestDsl.GETTERS_SETTERS.value)               : 'keepGettersSettersTogether',
          (RearrangerTestDsl.GETTERS_SETTERS_WITH_PROPERTY.value) : 'keepGettersSettersWithProperty'
        ]
        for (i in [value].flatten()) {
          settings."${m[i]}" = true
        }
        break
      case RearrangerTestDsl.OVERLOADED_METHODS.value:
        setIf(RearrangerTestDsl.ORDER, attributes, 'overloadedOrder', settings)
        setIf(RearrangerTestDsl.KEEP_TOGETHER, attributes, 'keepOverloadedMethodsTogether', settings)
    }
    settings
  }
}
