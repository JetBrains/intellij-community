package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;

import java.net.MalformedURLException;
import java.net.URL;

public class MavenDomElementDescriptorProvider implements XmlElementDescriptorProvider {
  public static XmlNSDescriptorImpl ourDescriptor;
  public static Object ourLock = new Object();

  public XmlElementDescriptor getDescriptor(XmlTag tag) {
    if (!MavenDomUtil.isPomFile(tag.getContainingFile())) return null;

    if (ourDescriptor == null) {
      synchronized (ourLock) {
        if (ourDescriptor == null) initDescriptor(tag);
      }
    }
    return ourDescriptor.getElementDescriptor(tag.getName(),
                                              ourDescriptor.getDefaultNamespace());
  }

  private void initDescriptor(XmlTag tag) {
    ourDescriptor = new XmlNSDescriptorImpl();

    String schemaUrl = MavenSchemaRegistrar.MAVEN_SCHEMA_URL;
    String location = ExternalResourceManager.getInstance().getResourceLocation(schemaUrl);
    if (schemaUrl.equals(location)) return;

    VirtualFile schema = null;
    try {
      schema = VfsUtil.findFileByURL(new URL(location));
    }
    catch (MalformedURLException ignore) {
      return;
    }

    if (schema != null) {
      PsiFile psiFile = PsiManager.getInstance(tag.getProject()).findFile(schema);
      if(psiFile != null) ourDescriptor.init(psiFile);
    }
  }
}
