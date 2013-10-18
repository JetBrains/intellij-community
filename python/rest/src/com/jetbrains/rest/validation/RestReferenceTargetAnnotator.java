package com.jetbrains.rest.validation;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.psi.RestReferenceTarget;

/**
 * Looks for double defined hyperlinks
 *
 * User : catherine
 */
public class RestReferenceTargetAnnotator extends RestAnnotator {

  @Override
  public void visitReferenceTarget(final RestReferenceTarget node) {
    RestFile file = (RestFile)node.getContainingFile();
    RestReferenceTarget[] targets = PsiTreeUtil.getChildrenOfType(file, RestReferenceTarget.class);
    String quotedName = node.getReferenceName();
    String name = node.getReferenceName(false);
    if (targets != null) {
      if ("__".equals(name) && !node.hasReference()) {
        getHolder().createWarningAnnotation(node, RestBundle.message("ANN.unusable.anonymous.target"));
      }
      for (RestReferenceTarget element : targets) {
        if ((element.getReferenceName().equalsIgnoreCase(name) || element.getReferenceName(false).equalsIgnoreCase(name) ||
            element.getReferenceName().equalsIgnoreCase(quotedName) || element.getReferenceName(false).equalsIgnoreCase(quotedName)) &&
                                    !element.equals(node) && ! "__".equals(name) && !"[#]".equals(quotedName) && !"[*]".equals(quotedName)) {
          getHolder().createWarningAnnotation(element, RestBundle.message("ANN.duplicate.target", name));
        }
      }
    }
  }
}
