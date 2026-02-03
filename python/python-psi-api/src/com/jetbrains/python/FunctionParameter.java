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
package com.jetbrains.python;

import org.jetbrains.annotations.Nullable;

/**
 * This class (possibly enum) represents function parameter
 *
 * @author Ilya.Kazakevich
 */
public interface FunctionParameter {
  /**
   * Position value if argument is keyword-only
   */
  int POSITION_NOT_SUPPORTED = -1;

  /**
   * @return parameter position. Be sure to check position is supported (!= {@link #POSITION_NOT_SUPPORTED} )
   * @see #POSITION_NOT_SUPPORTED
   */
  int getPosition();

  /**
   * @return parameter name (if known)
   */
  @Nullable
  String getName();
}
