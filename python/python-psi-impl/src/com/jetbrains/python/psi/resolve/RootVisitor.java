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
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
* Simple visitor to use with ResolveImportUtil.
* User: dcheryasov
*/
public interface RootVisitor {
  /**
   * @param root what we're visiting.
   * @param module the module to which the root belongs, or null
   * @param sdk the SDK to which the root belongs, or null
   *
   * @return false when visiting must stop.
   */
  boolean visitRoot(VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource);
}
