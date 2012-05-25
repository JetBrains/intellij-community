package com.wrq.rearranger.util.java

import com.wrq.rearranger.util.AbstractRuleBuilder
import com.wrq.rearranger.settings.RearrangerSettings
import com.wrq.rearranger.util.RearrangerTestDsl

/** 
 * @author Denis Zhdanov
 * @since 5/21/12 12:54 PM
 */
class JavaSpacingRule extends AbstractRuleBuilder<Void> {
  
  {
    def spacingAnchorToProperty = [
            (SpacingAnchor.AFTER_CLASS_LBRACE)   : 'afterClassLBrace',
            (SpacingAnchor.BEFORE_CLASS_RBRACE)  : 'beforeClassRBrace',
            (SpacingAnchor.AFTER_CLASS_RBRACE)   : 'afterClassRBrace',
            (SpacingAnchor.AFTER_METHOD_LBRACE)  : 'afterMethodLBrace',
            (SpacingAnchor.AFTER_METHOD_RBRACE)  : 'afterMethodRBrace',
            (SpacingAnchor.BEFORE_METHOD_LBRACE) : 'beforeMethodLBrace',
            (SpacingAnchor.BEFORE_METHOD_RBRACE) : 'beforeMethodRBrace',
            (SpacingAnchor.EOF)                  : 'newLinesAtEOF'
    ]
    registerHandler(RearrangerTestDsl.SPACING, {value, attributes, rule ->
      for (i in attributes[RearrangerTestDsl.ANCHOR.value]) {
        def s = settings."${spacingAnchorToProperty[i]}"
        s.force = true
        s.nBlankLines = attributes[RearrangerTestDsl.BLANK_LINES.value]
      }
      if (attributes[RearrangerTestDsl.REMOVE_BLANK_LINES.value]) {
        settings.removeBlanksInsideCodeBlocks = true
      }
    })
  }
  
  @Override
  protected Void createRule() {
    null
  }

  @Override
  protected void registerRule(RearrangerSettings settings, Void rule) {
  }
}
