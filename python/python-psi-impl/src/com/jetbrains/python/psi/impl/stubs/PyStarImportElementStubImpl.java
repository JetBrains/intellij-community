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
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;

/**
 * @author vlan
 */
public class PyStarImportElementStubImpl extends StubBase<PyStarImportElement> implements PyStarImportElementStub {
  protected PyStarImportElementStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.STAR_IMPORT_ELEMENT);
  }
}
