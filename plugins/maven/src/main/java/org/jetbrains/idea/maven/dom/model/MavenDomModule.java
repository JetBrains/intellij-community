// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.psi.PsiFile;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenModuleConverter;

/**
 * http://maven.apache.org/POM/4.0.0:modulesElemType interface.
 */
@Convert(MavenModuleConverter.class)
public interface MavenDomModule extends GenericDomValue<PsiFile>, MavenDomElement {
}
