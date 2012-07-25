package com.jetbrains.rest.validation;

import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.psi.RestReference;

/**
 * Looks for not defined hyperlinks
 *
 * User : catherine
 */
public class RestHyperlinksAnnotator extends RestAnnotator {

  @Override
  public void visitReference(final RestReference node) {
    if (node.getText().matches("`[^`]*<[^`]+>`_(_)?"))
      return;

    if (node.resolve() == null)
      getHolder().createWarningAnnotation(node, RestBundle.message("ANN.unknown.target", node.getReferenceText()));
  }
}
