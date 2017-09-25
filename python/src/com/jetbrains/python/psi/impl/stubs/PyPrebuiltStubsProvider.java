/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.PrebuiltStubsProviderBase;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyPrebuiltStubsProvider extends PrebuiltStubsProviderBase {
  @Override
  protected int getStubVersion() {
    return PyFileElementType.INSTANCE.getStubVersion();
  }

  @NotNull
  @Override
  protected String getName() {
    return PythonFileType.INSTANCE.getName();
  }
}
