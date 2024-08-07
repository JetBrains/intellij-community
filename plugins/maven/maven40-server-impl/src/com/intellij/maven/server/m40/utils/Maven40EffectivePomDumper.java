// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import com.intellij.maven.server.m40.Maven40ServerEmbedderImpl;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.jdom.*;
import org.jdom.filter2.ElementFilter;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.server.security.ChecksumUtil;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Maven40EffectivePomDumper {

  /**
   * The POM XSD URL
   */
  private static final String POM_XSD_URL = "https://maven.apache.org/maven-v4_0_0.xsd";

  /**
   * The Settings XSD URL
   */
  private static final String SETTINGS_XSD_URL = "http://maven.apache.org/xsd/settings-1.0.0.xsd";

  public static @Nullable String dependencyHash(@Nullable MavenProject project) {
    if (null == project) return null;

    Model model = project.getModel();
    if (null == model) return null;

    StringBuilder stringBuilder = new StringBuilder();
    List<Dependency> dependencies = model.getDependencies();
    stringBuilder.append(dependencyListHash(dependencies));

    DependencyManagement dependencyManagement = model.getDependencyManagement();
    if (null != dependencyManagement) {
      stringBuilder.append(dependencyListHash(dependencyManagement.getDependencies()));
    }

    return ChecksumUtil.checksum(stringBuilder.toString());
  }

  private static @NotNull StringBuffer dependencyListHash(@Nullable List<Dependency> dependencies ) {
    StringBuffer stringBuffer = new StringBuffer();
    if (null == dependencies || dependencies.isEmpty()) return stringBuffer;

    for (Dependency dependency : dependencies) {
      append(stringBuffer, dependency.getGroupId());
      append(stringBuffer, dependency.getArtifactId());
      append(stringBuffer, dependency.getVersion());
      append(stringBuffer, dependency.getType());
      append(stringBuffer, dependency.getClassifier());
      append(stringBuffer, dependency.getScope());
      append(stringBuffer, dependency.getSystemPath());

      List<Exclusion> exclusions = dependency.getExclusions();
      if (null != exclusions) {
        for (Exclusion exclusion : exclusions) {
          append(stringBuffer, exclusion.getArtifactId());
          append(stringBuffer, exclusion.getGroupId());
        }
      }

      append(stringBuffer, dependency.getOptional());
    }

    return stringBuffer;
  }

  private static void append(StringBuffer buffer, String s) {
    if (null != s) buffer.append(s);
  }

  // See org.apache.maven.plugins.help.EffectivePomMojo#execute from maven-help-plugin
  @Nullable
  public static String evaluateEffectivePom(Maven40ServerEmbedderImpl embedder,
                                            @NotNull final File file,
                                            @NotNull List<String> activeProfiles,
                                            @NotNull List<String> inactiveProfiles) {
    final StringWriter w = new StringWriter();

    try {
      final MavenExecutionRequest request = embedder.createRequest(file, activeProfiles, inactiveProfiles);

      embedder.executeWithMavenSession(request, MavenWorkspaceMap.empty(), null, session -> {
        try {
          // copied from DefaultMavenProjectBuilder.buildWithDependencies
          ProjectBuilder builder = embedder.getComponent(ProjectBuilder.class);
          ProjectBuildingResult buildingResult = builder.build(new File(file.getPath()), request.getProjectBuildingRequest());

          MavenProject project = buildingResult.getProject();

          XMLWriter writer = new PrettyPrintXMLWriter(new PrintWriter(w), StringUtils.repeat(" ", XmlWriterUtil.DEFAULT_INDENTATION_SIZE),
                                                      "\n", null, null);

          writeHeader(writer);

          writeEffectivePom(project, writer);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }
    catch (Exception e) {
      return null;
    }

    return w.toString();
  }

  /**
   * org.apache.maven.plugins.help.EffectivePomMojo#writeEffectivePom
   */
  private static void writeEffectivePom(MavenProject project, XMLWriter writer)
    throws MojoExecutionException {
    Model pom = project.getModel();
    cleanModel(pom);

    String effectivePom;

    StringWriter sWriter = new StringWriter();
    MavenXpp3Writer pomWriter = new MavenXpp3Writer();
    try {
      pomWriter.write(sWriter, pom);
    }
    catch (IOException e) {
      throw new MojoExecutionException("Cannot serialize POM to XML.", e);
    }

    effectivePom = addMavenNamespace(sWriter.toString(), true);

    writeComment(writer, "Effective POM for project '" + project.getId() + "'");

    writer.writeMarkup(effectivePom);
  }

  /**
   * org.jetbrains.idea.maven.server.MavenEffectivePomDumper#cleanModel(org.apache.maven.model.Model)
   */
  private static void cleanModel(Model pom) {
    Properties properties = new SortedProperties();
    properties.putAll(pom.getProperties());
    pom.setProperties(properties);
  }


  /**
   * Copy/pasted from org.apache.maven.plugins.help.AbstractEffectiveMojo#writeHeader
   */
  private static void writeHeader(XMLWriter writer) {
    XmlWriterUtil.writeCommentLineBreak(writer);
    XmlWriterUtil.writeComment(writer, " ");
    // Use ISO8601-format for date and time
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
    XmlWriterUtil.writeComment(writer, "Generated on " + dateFormat.format(new Date(System.currentTimeMillis())));
    XmlWriterUtil.writeComment(writer, " ");
    XmlWriterUtil.writeCommentLineBreak(writer);

    XmlWriterUtil.writeLineBreak(writer);
  }

  /**
   * Copy/pasted from org.apache.maven.plugins.help.AbstractEffectiveMojo
   */
  private static void writeComment(XMLWriter writer, String comment) {
    XmlWriterUtil.writeCommentLineBreak(writer);
    XmlWriterUtil.writeComment(writer, " ");
    XmlWriterUtil.writeComment(writer, comment);
    XmlWriterUtil.writeComment(writer, " ");
    XmlWriterUtil.writeCommentLineBreak(writer);

    XmlWriterUtil.writeLineBreak(writer);
  }

  private static boolean hasLineBreak(Element e) {
    return !e.getChildren().isEmpty() || e.getText().contains("\n");
  }

  private static boolean isOneEOFText(String text) {
    int eof = text.indexOf('\n');
    return eof != -1 && eof == text.lastIndexOf('\n') && text.trim().isEmpty();
  }

  private static void addLineBreaks(Element element) {
    List<Content> children = element.getContent();

    for (int i = 0; i < children.size() - 2; i++) {
      Content c1 = children.get(i);
      Content c2 = children.get(i + 1);
      Content c3 = children.get(i + 2);

      if (c1 instanceof Element && c2 instanceof Text && c3 instanceof Element
          && (hasLineBreak((Element)c1) || hasLineBreak((Element)c3))
          && isOneEOFText(((Text)c2).getText())) {
        element.setContent(i + 1, new Text(((Text)c2).getText().replace("\n", "\n\n")));
      }
    }
  }

  private static void addLineBreaks(Document pomXml, Namespace pomNamespace) {
    Element rootElement = pomXml.getRootElement();

    addLineBreaks(rootElement);

    Element buildElement = rootElement.getChild("build", pomNamespace);
    if (buildElement != null) {
      addLineBreaks(buildElement);
    }
  }

  /**
   * Copy/pasted from org.apache.maven.plugins.help.AbstractEffectiveMojo
   */
  private static String addMavenNamespace(String effectiveXml, boolean isPom) {
    SAXBuilder builder = new SAXBuilder();

    try {
      Document document = builder.build(new StringReader(effectiveXml));
      Element rootElement = document.getRootElement();

      // added namespaces
      Namespace pomNamespace = Namespace.getNamespace("", "http://maven.apache.org/POM/4.0.0");
      rootElement.setNamespace(pomNamespace);

      Namespace xsiNamespace = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
      rootElement.addNamespaceDeclaration(xsiNamespace);
      if (rootElement.getAttribute("schemaLocation", xsiNamespace) == null) {
        rootElement.setAttribute("schemaLocation", "http://maven.apache.org/POM/4.0.0 "
                                                   + (isPom ? POM_XSD_URL : SETTINGS_XSD_URL), xsiNamespace);
      }

      ElementFilter elementFilter = new ElementFilter(Namespace.getNamespace(""));
      for (Iterator<Element> i = rootElement.getDescendants(elementFilter); i.hasNext(); ) {
        Element e = i.next();
        e.setNamespace(pomNamespace);
      }

      addLineBreaks(document, pomNamespace);

      StringWriter w = new StringWriter();
      Format format = Format.getRawFormat();
      XMLOutputter out = new XMLOutputter(format);
      out.output(document.getRootElement(), w);

      return w.toString();
    }
    catch (JDOMException | IOException e) {
      return effectiveXml;
    }
  }

  /**
   * Properties which provides a sorted keySet().
   */
  protected static class SortedProperties
    extends Properties {
    /**
     * serialVersionUID
     */
    static final long serialVersionUID = -8985316072702233744L;

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Object> keySet() {
      Set<Object> keys = super.keySet();
      List<Object> list = new ArrayList<Object>(keys);
      Collections.sort(list, null);

      return new LinkedHashSet<Object>(list);
    }
  }
}
