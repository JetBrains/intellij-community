package com.jetbrains.python.psi;

import java.util.List;

/**
 * Generator expression PSI.
 *
 * @author yole
 */
public interface PyGeneratorExpression extends PyExpression, NameDefiner {
  PyExpression getResultExpression();
  List<ComprhForComponent> getForComponents();
  List<ComprhIfComponent> getIfComponents();
}
