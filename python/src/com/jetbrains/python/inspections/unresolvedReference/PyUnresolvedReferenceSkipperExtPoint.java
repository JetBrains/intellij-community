package com.jetbrains.python.inspections.unresolvedReference;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import org.jetbrains.annotations.NotNull;

/**
 * Inject this point to ask "unused reference" inspection to skip some unused references.
 * For example in Django you may import "I18N" to your "settings.py". It is not used in "settings.py", but used by Django
 * and should not be marked as "unused".
 *
 * @author Ilya.Kazakevich
 */
public interface PyUnresolvedReferenceSkipperExtPoint {
  @NotNull
  ExtensionPointName<PyUnresolvedReferenceSkipperExtPoint> EP_NAME = ExtensionPointName.create("Pythonid.unresolvedReferenceSkipper");

  /**
   * Checks if some unused import should be skipped
   *
   * @param importNameDefiner unused import
   * @return true if should be skipped
   */
  boolean unusedImportShouldBeSkipped(@NotNull PyImportedNameDefiner importNameDefiner);
}
