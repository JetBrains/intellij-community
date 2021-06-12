// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import java.util.List;


public interface PyComprehensionElement extends PyExpression, PyNamedElementContainer {
  PyExpression getResultExpression();
  List<PyComprehensionComponent> getComponents();
  List<PyComprehensionForComponent> getForComponents();
  List<PyComprehensionIfComponent> getIfComponents();
}
