
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

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="repoType" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="effectiveLocalStorageUrl" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="proxyUrl" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="sizeOnDisk" type="{http://www.w3.org/2001/XMLSchema}integer"/>
 *         &lt;element name="numArtifacts" type="{http://www.w3.org/2001/XMLSchema}integer"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "id",
    "repoType",
    "effectiveLocalStorageUrl",
    "proxyUrl",
    "sizeOnDisk",
    "numArtifacts"
})
@XmlRootElement(name = "repositoryMetaData")
public class RepositoryMetaData {

    @XmlElement(required = true)
    protected String id;
    @XmlElement(required = true)
    protected String repoType;
    @XmlElement(required = true)
    protected String effectiveLocalStorageUrl;
    @XmlElement(required = true)
    protected String proxyUrl;
    @XmlElement(required = true)
    protected BigInteger sizeOnDisk;
    @XmlElement(required = true)
    protected BigInteger numArtifacts;

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the repoType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRepoType() {
        return repoType;
    }

    /**
     * Sets the value of the repoType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRepoType(String value) {
        this.repoType = value;
    }

    /**
     * Gets the value of the effectiveLocalStorageUrl property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEffectiveLocalStorageUrl() {
        return effectiveLocalStorageUrl;
    }

    /**
     * Sets the value of the effectiveLocalStorageUrl property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEffectiveLocalStorageUrl(String value) {
        this.effectiveLocalStorageUrl = value;
    }

    /**
     * Gets the value of the proxyUrl property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProxyUrl() {
        return proxyUrl;
    }

    /**
     * Sets the value of the proxyUrl property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProxyUrl(String value) {
        this.proxyUrl = value;
    }

    /**
     * Gets the value of the sizeOnDisk property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getSizeOnDisk() {
        return sizeOnDisk;
    }

    /**
     * Sets the value of the sizeOnDisk property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setSizeOnDisk(BigInteger value) {
        this.sizeOnDisk = value;
    }

    /**
     * Gets the value of the numArtifacts property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getNumArtifacts() {
        return numArtifacts;
    }

    /**
     * Sets the value of the numArtifacts property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setNumArtifacts(BigInteger value) {
        this.numArtifacts = value;
    }

}
