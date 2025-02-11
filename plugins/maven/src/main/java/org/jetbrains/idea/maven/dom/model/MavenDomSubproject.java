// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model;

import com.intellij.psi.PsiFile;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenModuleConverter;

@Convert(MavenModuleConverter.class)
public interface MavenDomSubproject extends GenericDomValue<PsiFile>, MavenDomElement {
}