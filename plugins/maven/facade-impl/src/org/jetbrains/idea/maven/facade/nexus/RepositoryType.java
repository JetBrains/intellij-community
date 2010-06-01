
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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;


/**
 * <p>Java class for repositoryType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="repositoryType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="resourceURI" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="contentResourceURI" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="repoType" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="repoPolicy" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="provider" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="providerRole" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="format" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="userManaged" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="exposed" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="effectiveLocalStorageUrl" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "repositoryType", propOrder = {
    "resourceURI",
    "contentResourceURI",
    "id",
    "name",
    "repoType",
    "repoPolicy",
    "provider",
    "providerRole",
    "format",
    "userManaged",
    "exposed",
    "effectiveLocalStorageUrl"
})
public class RepositoryType implements Serializable {

    @XmlElement(required = true)
    protected String resourceURI;
    @XmlElement(required = true)
    protected String contentResourceURI;
    @XmlElement(required = true)
    protected String id;
    @XmlElement(required = true)
    protected String name;
    @XmlElement(required = true)
    protected String repoType;
    @XmlElement(required = true)
    protected String repoPolicy;
    @XmlElement(required = true)
    protected String provider;
    @XmlElement(required = true)
    protected String providerRole;
    @XmlElement(required = true)
    protected String format;
    @XmlElement(required = true)
    protected String userManaged;
    @XmlElement(required = true)
    protected String exposed;
    @XmlElement(required = true)
    protected String effectiveLocalStorageUrl;

    /**
     * Gets the value of the resourceURI property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getResourceURI() {
        return resourceURI;
    }

    /**
     * Sets the value of the resourceURI property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setResourceURI(String value) {
        this.resourceURI = value;
    }

    /**
     * Gets the value of the contentResourceURI property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContentResourceURI() {
        return contentResourceURI;
    }

    /**
     * Sets the value of the contentResourceURI property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContentResourceURI(String value) {
        this.contentResourceURI = value;
    }

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
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
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
     * Gets the value of the repoPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRepoPolicy() {
        return repoPolicy;
    }

    /**
     * Sets the value of the repoPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRepoPolicy(String value) {
        this.repoPolicy = value;
    }

    /**
     * Gets the value of the provider property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Sets the value of the provider property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProvider(String value) {
        this.provider = value;
    }

    /**
     * Gets the value of the providerRole property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProviderRole() {
        return providerRole;
    }

    /**
     * Sets the value of the providerRole property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProviderRole(String value) {
        this.providerRole = value;
    }

    /**
     * Gets the value of the format property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFormat() {
        return format;
    }

    /**
     * Sets the value of the format property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFormat(String value) {
        this.format = value;
    }

    /**
     * Gets the value of the userManaged property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUserManaged() {
        return userManaged;
    }

    /**
     * Sets the value of the userManaged property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUserManaged(String value) {
        this.userManaged = value;
    }

    /**
     * Gets the value of the exposed property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getExposed() {
        return exposed;
    }

    /**
     * Sets the value of the exposed property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setExposed(String value) {
        this.exposed = value;
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

}
