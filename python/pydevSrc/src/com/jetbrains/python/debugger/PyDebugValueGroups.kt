// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import com.jetbrains.python.debugger.pydev.ProcessDebugger
import javax.swing.Icon


const val DUNDER_LEN = "__len__"
const val DUNDER_EX = "__exception__"
val PROTECTED_ATTRS_EXCLUDED = setOf(DUNDER_LEN, DUNDER_EX)

open class PyXValueGroup(groupName: String, val groupType: ProcessDebugger.GROUP_TYPE) : XValueGroup(groupName)


fun extractChildrenToGroup(groupName: String,
                           icon: Icon,
                           node: XCompositeNode,
                           children: XValueChildrenList,
                           predicate: (String) -> Boolean,
                           excludedNames: Set<String>) {
  val filterResult = filterChildren(children, predicate, excludedNames)
  node.addChildren(filterResult.filteredChildren, filterResult.groupElements.isEmpty())
  addGroupValues(groupName, icon, node, filterResult.groupElements, null, ProcessDebugger.GROUP_TYPE.DEFAULT, null)
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
                   groupElements: Map<String, XValue>?,
                   myDebugProcess: PyFrameAccessor?,
                   groupType: ProcessDebugger.GROUP_TYPE,
                   nameSuffix: String?) {
  val group = object : PyXValueGroup(groupName, groupType) {
    override fun computeChildren(node: XCompositeNode) {
      if (node.isObsolete) return
      ApplicationManager.getApplication().executeOnPooledThread {
        val list: XValueChildrenList? =
          if (groupType == ProcessDebugger.GROUP_TYPE.DEFAULT) {
            getDefaultGroupNodes(groupElements, nameSuffix)
          }
          else {
            myDebugProcess?.let { getSpecialGroupNodes(it, nameSuffix, groupType) }
          }
        list?.let { node.addChildren(list, true) }
      }
    }

    override fun getIcon(): Icon {
      return groupIcon
    }
  }
  node.addChildren(XValueChildrenList.bottomGroup(group), true)
}

private fun getDefaultGroupNodes(groupElements: Map<String, XValue>?, nameSuffix: String?): XValueChildrenList {
  val list = XValueChildrenList()
  groupElements?.let {
    for ((key, value) in groupElements) {
      val name = addSuffix(key, nameSuffix)
      list.add(name, value)
    }
  }
  return list
}

private fun getSpecialGroupNodes(myDebugProcess: PyFrameAccessor,
                                 nameSuffix: String?,
                                 groupType: ProcessDebugger.GROUP_TYPE): XValueChildrenList {
  return addSuffix(myDebugProcess.loadSpecialVariables(groupType) ?: XValueChildrenList(), nameSuffix)
}

private fun addSuffix(list: XValueChildrenList, nameSuffix: String?): XValueChildrenList {
  nameSuffix?.let {
    val result = XValueChildrenList()
    for (i in 0 until list.size()) {
      val value = list.getValue(i)
      val name = list.getName(i)
      result.add(addSuffix(name, it), value)
    }
    return result
  }
  return list

}

private fun addSuffix(name: String, nameSuffix: String?): String = if (nameSuffix == null) name else name + nameSuffix