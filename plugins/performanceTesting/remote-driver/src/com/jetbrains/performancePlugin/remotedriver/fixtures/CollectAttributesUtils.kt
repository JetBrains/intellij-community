// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import org.assertj.swing.core.GenericTypeMatcher
import org.assertj.swing.core.Robot
import java.awt.Component
import java.awt.Container

internal fun Component.collectTextAttributes(robot: Robot): List<Pair<String, SimpleTextAttributes>> {
  val component = this
  val result = mutableListOf<Pair<String, SimpleTextAttributes>>()

  if (component is SimpleColoredComponent) {
    result.addAll(component.collectTextAttributes())
  }
  else if (component is Container) {
    val components =
      robot.finder().findAll(component, object : GenericTypeMatcher<SimpleColoredComponent>(SimpleColoredComponent::class.java) {
        override fun isMatching(component: SimpleColoredComponent): Boolean {
          return true
        }
      })
    for (c in components) {
      result.addAll(c.collectTextAttributes())
    }
  }

  return result
}

private fun SimpleColoredComponent.collectTextAttributes(): List<Pair<String, SimpleTextAttributes>> = buildList {
  val iter = this@collectTextAttributes.iterator()
  while (iter.hasNext()) {
    val text = iter.next()
    add(text to iter.textAttributes)
  }
}
