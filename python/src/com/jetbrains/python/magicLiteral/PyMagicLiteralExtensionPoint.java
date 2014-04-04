package com.jetbrains.python.magicLiteral;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Any magic literal extension point should imlement this interface and be installed as extesnion point
 * using {@link #EP_NAME}
 *
 * @author Ilya.Kazakevich
 */
public interface PyMagicLiteralExtensionPoint {

  ExtensionPointName<PyMagicLiteralExtensionPoint> EP_NAME = ExtensionPointName.create("Pythonid.magicLiteral");


  /**
   * Checks if literal is magic and supported by this extension point.
   * @param element element to check
   * @return true if magic.
   */
  boolean isMagicLiteral(@NotNull PyStringLiteralExpression element);


  /**
   * @return human-readable type of this literal. Actually, that is extension point name
   */
  @NotNull
  String getLiteralType();
}
