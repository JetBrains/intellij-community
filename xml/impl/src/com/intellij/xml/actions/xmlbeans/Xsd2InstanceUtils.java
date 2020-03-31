// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions.xmlbeans;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.tool.CommandLine;
import org.apache.xmlbeans.impl.xsd2inst.SampleXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;


/**
 * @author Konstantin Bulenkov
 */
public class Xsd2InstanceUtils {
    public static String generate(String[] args) {
        Set flags = new HashSet();
        Set opts = new HashSet();
        flags.add("h");
        flags.add("help");
        flags.add("usage");
        flags.add("license");
        flags.add("version");
        flags.add("dl");
        flags.add("noupa");
        flags.add("nopvr");
        flags.add("partial");
        opts.add("name");

        CommandLine cl = new CommandLine(args, flags, opts);

        String[] badOpts = cl.getBadOpts();
        if (badOpts.length > 0) {
                throw new IllegalArgumentException("Unrecognized option: " + badOpts[0]);

        }

        boolean dl = (cl.getOpt("dl") != null);
        boolean nopvr = (cl.getOpt("nopvr") != null);
        boolean noupa = (cl.getOpt("noupa") != null);

        File[] schemaFiles = cl.filesEndingWith(".xsd");
        String rootName = cl.getOpt("name");

        if (rootName == null) {
            throw new IllegalArgumentException("Required option \"-name\" must be present");
        }

        // Process Schema files
        List sdocs = new ArrayList();
      for (File schemaFile : schemaFiles) {
        try {
          sdocs.add(XmlObject.Factory.parse(schemaFile,
                                            (new XmlOptions()).setLoadLineNumbers().setLoadMessageDigest()));
        }
        catch (Exception e) {
          throw new IllegalArgumentException("Can not load schema file: " + schemaFile + ": " + e.getLocalizedMessage());
        }
      }

        XmlObject[] schemas = (XmlObject[]) sdocs.toArray(new XmlObject[0]);

        SchemaTypeSystem sts = null;
        if (schemas.length > 0)
        {
            XmlOptions compileOptions = new XmlOptions();
            if (dl)
                compileOptions.setCompileDownloadUrls();
            if (nopvr)
                compileOptions.setCompileNoPvrRule();
            if (noupa)
                compileOptions.setCompileNoUpaRule();

          try {
            sts = XmlBeans.compileXsd(schemas, XmlBeans.getBuiltinTypeSystem(), compileOptions);
          }
          catch (XmlException e) {
            StringBuilder out = new StringBuilder("Schema compilation errors: ");
            Collection errors = e.getErrors();
            for (Object error : errors) out.append("\n").append(error);
              throw new IllegalArgumentException(out.toString());
          }
        }

        if (sts == null)
        {
            throw new IllegalArgumentException("No Schemas to process.");
        }
        SchemaType[] globalElems = sts.documentTypes();
        SchemaType elem = null;
      for (SchemaType globalElem : globalElems) {
        if (rootName.equals(globalElem.getDocumentElementName().getLocalPart())) {
          elem = globalElem;
          break;
        }
      }

        if (elem == null) {
            throw new IllegalArgumentException("Could not find a global element with name \"" + rootName + "\"");
        }

        // Now generate it
        return SampleXmlUtil.createSampleForType(elem);
    }

  public static XmlElementDescriptor getDescriptor(XmlTag tag, String elementName) {
    final PsiMetaData metaData = tag.getMetaData();

    if (metaData instanceof XmlNSDescriptorImpl) {
      final XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl) metaData;
      return nsDescriptor.getElementDescriptor(elementName, nsDescriptor.getDefaultNamespace());
    }

    return null;
  }

  public static List<String> addVariantsFromRootTag(XmlTag rootTag) {
    PsiMetaData metaData = rootTag.getMetaData();
    if (metaData instanceof XmlNSDescriptorImpl) {
      XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl) metaData;

      List<String> elementDescriptors = new ArrayList<>();
      XmlElementDescriptor[] rootElementsDescriptors = nsDescriptor.getRootElementsDescriptors(PsiTreeUtil.getParentOfType(rootTag, XmlDocument.class));
      for(XmlElementDescriptor e:rootElementsDescriptors) {
        elementDescriptors.add(e.getName());
      }

      return elementDescriptors;
    }
    return Collections.emptyList();
  }

  public static String processAndSaveAllSchemas(@NotNull XmlFile file, @NotNull final Map<String, String> scannedToFileName,
                                          final @NotNull SchemaReferenceProcessor schemaReferenceProcessor) {
    final String fileName = file.getName();

    String previous = scannedToFileName.get(fileName);

    if (previous != null) return previous;

    scannedToFileName.put(fileName, fileName);

    final StringBuilder result = new StringBuilder();

    file.acceptChildren(new XmlRecursiveElementVisitor() {
      @Override public void visitElement(@NotNull PsiElement psiElement) {
        super.visitElement(psiElement);
        if (psiElement instanceof LeafPsiElement) {
          final String text = psiElement.getText();
          result.append(text);
        }
      }

      @Override public void visitXmlAttribute(XmlAttribute xmlAttribute) {
        boolean replaced = false;

        if (xmlAttribute.isNamespaceDeclaration()) {
          replaced = true;
          final String value = xmlAttribute.getValue();
          result.append(xmlAttribute.getText()).append(" ");

          if (!scannedToFileName.containsKey(value)) {
            final XmlNSDescriptor nsDescriptor = xmlAttribute.getParent().getNSDescriptor(value, true);

            if (nsDescriptor != null) {
              processAndSaveAllSchemas(nsDescriptor.getDescriptorFile(), scannedToFileName, schemaReferenceProcessor);
            }
          }
        } else if ("schemaLocation".equals(xmlAttribute.getName())) {
          final PsiReference[] references = xmlAttribute.getValueElement().getReferences();

          PsiReference reference = ArrayUtil.getLastElement(references);
          if (reference != null) {
            PsiElement psiElement = reference.resolve();

            if (psiElement instanceof XmlFile) {
              final String s = processAndSaveAllSchemas(((XmlFile) psiElement), scannedToFileName, schemaReferenceProcessor);
              if (s != null) {
                result.append(xmlAttribute.getName()).append("='").append(s).append('\'');
                replaced = true;
              }
            }
          }
        }
        if (!replaced) result.append(xmlAttribute.getText());
      }
    });

    final VirtualFile virtualFile = file.getVirtualFile();
    final String content = result.toString();

    byte[] bytes;
    if (virtualFile != null) {
      bytes = content.getBytes(virtualFile.getCharset());
    } else {
      try {
        final String charsetName = XmlUtil.extractXmlEncodingFromProlog(content.getBytes(StandardCharsets.UTF_8));
        bytes = charsetName != null ? content.getBytes(charsetName) : content.getBytes(StandardCharsets.UTF_8);
      } catch (UnsupportedEncodingException e) {
        bytes = content.getBytes(StandardCharsets.UTF_8);
      }
    }

    schemaReferenceProcessor.processSchema(fileName, bytes);
    return fileName;
  }

  public interface SchemaReferenceProcessor {
    void processSchema(String schemaFileName, byte[] schemaContent);
  }
}
