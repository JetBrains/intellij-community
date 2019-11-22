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
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;

/**
 * Project service to cache {@link TypeEvalContext}
 * @author Ilya.Kazakevich
 */
public interface TypeEvalContextCache {
  /**
   * Returns context from cache (if exist) or returns the one you provided (and puts it into cache).
   * To use this method, do the following:
   * <ol>
   * <li>Instantiate {@link TypeEvalContext} you want to use</li>
   * <li>Pass its instance here as argument</li>
   * <li>Use result</li>
   * </ol>
   *
   * @param standard context you want to use. Just instantiate it and pass here.
   * @return context from cache (the one equals by constraints to yours or the one you provided)
   */
  @NotNull
  TypeEvalContext getContext(@NotNull TypeEvalContext standard);
}
