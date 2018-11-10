/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyClassType extends PyClassLikeType, UserDataHolder {
  @NotNull
  PyClass getPyClass();

  /**
   * @param name name to check
   * @param context type evaluation context
   * @return true if attribute with the specified name could be created or updated.
   * @see PyClass#getSlots(TypeEvalContext)
   */
  default boolean isAttributeWritable(@NotNull String name, @NotNull TypeEvalContext context) {
    return true;
  }
}
