package com.intellij.ide.starter.buildTool.events

import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent

// Here a string can be used instead of an object.
// The object is just used as an example
// When changing the class, remember to also change com.intellij.workspaceModel.performanceTesting.events.GradleDaemonEvent
class GradleDaemonEvent(val data: GradleEventData) : SharedEvent()