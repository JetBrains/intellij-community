/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.facade.nexus;

import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;


/**
 * <p>Java class for artifactType complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="artifactType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="resourceUri" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="groupId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="artifactId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="version" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="classifier" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="packaging" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="extension" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="repoId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="contextId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="pomLink" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="artifactLink" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "artifactType", propOrder = {
  "resourceUri",
  "groupId",
  "artifactId",
  "version",
  "classifier",
  "packaging",
  "extension",
  "repoId",
  "contextId",
  "pomLink",
  "artifactLink"
})
public class ArtifactType implements Serializable {

  @XmlElement(required = true)
  protected String resourceUri;
  @XmlElement(required = true)
  protected String groupId;
  @XmlElement(required = true)
  protected String artifactId;
  @XmlElement(required = true)
  protected String version;
  @XmlElement(required = true)
  protected String classifier;
  @XmlElement(required = true)
  protected String packaging;
  @XmlElement(required = true)
  protected String extension;
  @XmlElement(required = true)
  protected String repoId;
  @XmlElement(required = true)
  protected String contextId;
  @XmlElement(required = true)
  protected String pomLink;
  @XmlElement(required = true)
  protected String artifactLink;

  public ArtifactType() {

  }

  public ArtifactType(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  /**
   * Gets the value of the resourceUri property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getResourceUri() {
    return resourceUri;
  }

  /**
   * Sets the value of the resourceUri property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setResourceUri(String value) {
    this.resourceUri = value;
  }

  /**
   * Gets the value of the groupId property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getGroupId() {
    return groupId;
  }

  /**
   * Sets the value of the groupId property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setGroupId(String value) {
    this.groupId = value;
  }

  /**
   * Gets the value of the artifactId property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getArtifactId() {
    return artifactId;
  }

  /**
   * Sets the value of the artifactId property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setArtifactId(String value) {
    this.artifactId = value;
  }

  /**
   * Gets the value of the version property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getVersion() {
    return version;
  }

  /**
   * Sets the value of the version property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setVersion(String value) {
    this.version = value;
  }

  /**
   * Gets the value of the classifier property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getClassifier() {
    return classifier;
  }

  /**
   * Sets the value of the classifier property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setClassifier(String value) {
    this.classifier = value;
  }

  /**
   * Gets the value of the packaging property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getPackaging() {
    return packaging;
  }

  /**
   * Sets the value of the packaging property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setPackaging(String value) {
    this.packaging = value;
  }

  /**
   * Gets the value of the extension property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getExtension() {
    return extension;
  }

  /**
   * Sets the value of the extension property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setExtension(String value) {
    this.extension = value;
  }

  /**
   * Gets the value of the repoId property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getRepoId() {
    return repoId;
  }

  /**
   * Sets the value of the repoId property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setRepoId(String value) {
    this.repoId = value;
  }

  /**
   * Gets the value of the contextId property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getContextId() {
    return contextId;
  }

  /**
   * Sets the value of the contextId property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setContextId(String value) {
    this.contextId = value;
  }

  /**
   * Gets the value of the pomLink property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getPomLink() {
    return pomLink;
  }

  /**
   * Sets the value of the pomLink property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setPomLink(String value) {
    this.pomLink = value;
  }

  /**
   * Gets the value of the artifactLink property.
   *
   * @return possible object is
   *         {@link String }
   */
  public String getArtifactLink() {
    return artifactLink;
  }

  /**
   * Sets the value of the artifactLink property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setArtifactLink(String value) {
    this.artifactLink = value;
  }

  @Override
  public String toString() {
    return groupId + ":" + artifactId + ":" + version;
  }
}
