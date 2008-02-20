/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;

public class JavaHelperFactory implements HelperFactory.Factory {
  public Helper create(final FileType fileType, final Project project) {
    return new JavaHelper(fileType, project);
  }
}