// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import javax.swing.Icon


const val PROTECTED_ATTRS_NAME = "Protected Attributes"
const val DUNDER_LEN = "__len__"
val PROTECTED_ATTRS_EXCLUDED = setOf(DUNDER_LEN, "__exception__")


fun extractChildrenToGroup(groupName: String,
                           icon: Icon,
                           node: XCompositeNode,
                           children: XValueChildrenList,
                           predicate: (String) -> Boolean,
                           excludedNames: Set<String>) {
  val filterResult = filterChildren(children, predicate, excludedNames)
  node.addChildren(filterResult.filteredChildren, filterResult.groupElements.isEmpty())
  addGroupValues(groupName, icon, node, filterResult.groupElements, null)
}


private class FilterResult {
  val filteredChildren: XValueChildrenList = XValueChildrenList()
  val groupElements: MutableMap<String, XValue> = mutableMapOf()
}


private fun filterChildren(children: XValueChildrenList, predicate: (String) -> Boolean, excludedNames: Set<String>): FilterResult {
  val result = FilterResult()
  for (i in 0 until children.size()) {
    val value = children.getValue(i)
    val name = children.getName(i)
    if (value is PyDebugValue) {
      if (predicate(value.name) && !excludedNames.contains(value.name)) {
        result.groupElements[name] = value
      }
      else {
        result.filteredChildren.add(name, value)
      }
    }
  }
  return result
}

fun addGroupValues(groupName: String,
                   groupIcon: Icon,
                   node: XCompositeNode,
                   groupElements: Map<String, XValue>,
                   nameSuffix: String?) {
  if (groupElements.isEmpty()) return
  val group = object : XValueGroup(groupName) {
    override fun computeChildren(node: XCompositeNode) {
      val list = XValueChildrenList()
      for ((key, value) in groupElements) {
        val name = if (nameSuffix == null) key else key + nameSuffix
        list.add(name, value)
      }
      node.addChildren(list, true)
    }

    override fun getIcon(): Icon {
      return groupIcon
    }
  }
  node.addChildren(XValueChildrenList.bottomGroup(group), true)
}