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
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;
import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Experimental
public interface PyAstTypeDeclarationStatement extends PyAstStatement, PyAstAnnotationOwner {
  @NotNull
  default PyAstExpression getTarget() {
    return findNotNullChildByClass(this, PyAstExpression.class);
  }

  @Override
  @Nullable
  default PyAstAnnotation getAnnotation() {
    return findChildByClass(this, PyAstAnnotation.class);
  }
}
