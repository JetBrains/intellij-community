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
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Sep 28, 2008
 */
public class PyDecoratorListStubImpl extends StubBase<PyDecoratorList> implements PyDecoratorListStub {
  public PyDecoratorListStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.DECORATOR_LIST);
  }
}
