package com.jetbrains.python.psi;

import java.util.List;

/**
 * Dict comprehension: {x:x+1 for x in range(10)}
 * 
 * @author yole
 */
public interface PyDictCompExpression extends PyExpression, NameDefiner {
  PyExpression getResultExpression();
  List<ComprhForComponent> getForComponents();
  List<ComprhIfComponent> getIfComponents();
}
