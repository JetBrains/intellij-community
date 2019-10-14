package com.intellij.openapi.roots.impl

import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile

class DummyPushedFilePropertiesUpdater: PushedFilePropertiesUpdater() {
  override fun runConcurrentlyIfPossible(tasks: MutableList<Runnable>?) {
  }

  override fun initializeProperties() {
  }

  override fun pushAll(vararg pushers: FilePropertyPusher<*>?) {
  }

  override fun filePropertiesChanged(file: VirtualFile) {
  }

  override fun pushAllPropertiesNow() {
  }

  override fun <T : Any?> findAndUpdateValue(fileOrDir: VirtualFile?, pusher: FilePropertyPusher<T>?, moduleValue: T) {
  }

  override fun filePropertiesChanged(fileOrDir: VirtualFile, acceptFileCondition: Condition<in VirtualFile>) {
  }
}