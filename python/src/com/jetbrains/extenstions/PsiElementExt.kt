/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.extenstions

import com.intellij.psi.PsiElement
import java.util.*

/**
 * @param classOnly limit ancestors to this class only
 * @param limit upper limit to prevent huge unstub. [com.intellij.psi.PsiFile] is good choice
 */
fun <T : PsiElement> PsiElement.getAncestors(limit: PsiElement = this.containingFile, classOnly: Class<T>): List<T> {
  var currentElement = this
  val result = ArrayList<T>()
  while (currentElement != limit) {
    currentElement = currentElement.parent
    if (classOnly.isInstance(currentElement)) {
      @Suppress("UNCHECKED_CAST") // Checked one line above
      result.add(currentElement as T)
    }
  }

  return result
}