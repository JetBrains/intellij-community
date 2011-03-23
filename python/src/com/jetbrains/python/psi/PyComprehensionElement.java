package com.jetbrains.python.psi;

import java.util.List;

/**
 * @author yole
 */
public interface PyComprehensionElement extends PyExpression, NameDefiner {
  PyExpression getResultExpression();
  List<ComprehensionComponent> getComponents();
  List<ComprhForComponent> getForComponents();
  List<ComprhIfComponent> getIfComponents();
}
