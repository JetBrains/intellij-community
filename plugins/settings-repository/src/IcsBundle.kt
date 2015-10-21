/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.settingsRepository

import com.intellij.CommonBundle
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.*

private var ourBundle: Reference<ResourceBundle>? = null

private const val BUNDLE: String = "messages.IcsBundle"

fun icsMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): String {
  return CommonBundle.message(getBundle(), key, *params)
}

private fun getBundle(): ResourceBundle {
  var bundle: ResourceBundle? = null
  if (ourBundle != null) {
    bundle = ourBundle!!.get()
  }
  if (bundle == null) {
    bundle = ResourceBundle.getBundle(BUNDLE)
    ourBundle = SoftReference(bundle)
  }
  return bundle!!
}