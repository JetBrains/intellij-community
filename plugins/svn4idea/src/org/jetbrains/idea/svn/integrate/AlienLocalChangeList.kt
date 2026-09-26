// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import org.jetbrains.annotations.Nls

open class AlienLocalChangeList(private val myChanges: List<Change>, @Nls private var myName: String) : LocalChangeList() {
  private var myComment: String? = ""

  override fun getChanges(): Collection<Change> = myChanges

  override fun getName(): String = myName

  override fun getComment(): String? = myComment

  override fun isDefault(): Boolean = false

  override fun isReadOnly(): Boolean = false

  override fun getData(): Any? = throw UnsupportedOperationException()

  override fun copy(): LocalChangeList = throw UnsupportedOperationException()
}
