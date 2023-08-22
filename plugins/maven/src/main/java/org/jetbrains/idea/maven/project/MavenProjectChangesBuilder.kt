/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project

class MavenProjectChangesBuilder : MavenProjectChanges() {
  private var hasPackagingChanges = false
  private var hasOutputChanges = false
  private var hasSourceChanges = false
  private var hasDependencyChanges = false
  private var hasPluginsChanges = false
  private var hasPropertyChanges = false

  override fun hasPackagingChanges(): Boolean {
    return hasPackagingChanges
  }

  fun setHasPackagingChanges(value: Boolean) {
    hasPackagingChanges = value
  }

  override fun hasOutputChanges(): Boolean {
    return hasOutputChanges
  }

  fun setHasOutputChanges(value: Boolean) {
    hasOutputChanges = value
  }

  override fun hasSourceChanges(): Boolean {
    return hasSourceChanges
  }

  fun setHasSourceChanges(value: Boolean) {
    hasSourceChanges = value
  }

  override fun hasDependencyChanges(): Boolean {
    return hasDependencyChanges
  }

  fun setHasDependencyChanges(value: Boolean) {
    hasDependencyChanges = value
  }

  override fun hasPluginsChanges(): Boolean {
    return hasPluginsChanges
  }

  fun setHasPluginChanges(value: Boolean) {
    hasPluginsChanges = value
  }

  override fun hasPropertyChanges(): Boolean {
    return hasPropertyChanges
  }

  fun setHasPropertyChanges(value: Boolean) {
    hasPropertyChanges = value
  }

  fun setAllChanges(value: Boolean) {
    setHasPackagingChanges(value)
    setHasOutputChanges(value)
    setHasSourceChanges(value)
    setHasDependencyChanges(value)
    setHasPluginChanges(value)
    setHasPropertyChanges(value)
  }

  override fun toString(): String {
    return "MavenProjectChangesBuilder(hasPackagingChanges=$hasPackagingChanges, hasOutputChanges=$hasOutputChanges, hasSourceChanges=$hasSourceChanges, hasDependencyChanges=$hasDependencyChanges, hasPluginsChanges=$hasPluginsChanges, hasPropertyChanges=$hasPropertyChanges)"
  }

  companion object {
    @JvmStatic
    fun merged(a: MavenProjectChanges, b: MavenProjectChanges): MavenProjectChangesBuilder {
      val result = MavenProjectChangesBuilder()
      result.setHasPackagingChanges(a.hasPackagingChanges() || b.hasPackagingChanges())
      result.setHasOutputChanges(a.hasOutputChanges() || b.hasOutputChanges())
      result.setHasSourceChanges(a.hasSourceChanges() || b.hasSourceChanges())
      result.setHasDependencyChanges(a.hasDependencyChanges() || b.hasDependencyChanges())
      result.setHasPluginChanges(a.hasPluginsChanges() || b.hasPluginsChanges())
      result.setHasPropertyChanges(a.hasPropertyChanges() || b.hasPropertyChanges())
      return result
    }
  }
}