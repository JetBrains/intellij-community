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
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface PyClassStub extends NamedStub<PyClass> {

  /**
  * @return a {@code Map} which contains imported class names as keys and their original names as values
  */
  @NotNull
  Map<QualifiedName, QualifiedName> getSuperClasses();

  /**
   * Returns literal text of the subscription expressions in the base classes list.
   * It's intended to be used for resolving generic types and type parameters in PEP 484 notation.
   */
  @NotNull
  List<String> getSubscriptedSuperClasses();

  @Nullable
  QualifiedName getMetaClass();

  @Nullable
  List<String> getSlots();

  @Nullable
  String getDocString();
}