/**
 * ProfileData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class ProfileData  implements java.io.Serializable {
    private java.math.BigInteger id;

    private com.intellij.tasks.mantis.model.AccountData user_id;

    private java.lang.String platform;

    private java.lang.String os;

    private java.lang.String os_build;

    private java.lang.String description;

    public ProfileData() {
    }

    public ProfileData(
           java.math.BigInteger id,
           com.intellij.tasks.mantis.model.AccountData user_id,
           java.lang.String platform,
           java.lang.String os,
           java.lang.String os_build,
           java.lang.String description) {
           this.id = id;
           this.user_id = user_id;
           this.platform = platform;
           this.os = os;
           this.os_build = os_build;
           this.description = description;
    }


    /**
     * Gets the id value for this ProfileData.
     * 
     * @return id
     */
    public java.math.BigInteger getId() {
        return id;
    }


    /**
     * Sets the id value for this ProfileData.
     * 
     * @param id
     */
    public void setId(java.math.BigInteger id) {
        this.id = id;
    }


    /**
     * Gets the user_id value for this ProfileData.
     * 
     * @return user_id
     */
    public com.intellij.tasks.mantis.model.AccountData getUser_id() {
        return user_id;
    }


    /**
     * Sets the user_id value for this ProfileData.
     * 
     * @param user_id
     */
    public void setUser_id(com.intellij.tasks.mantis.model.AccountData user_id) {
        this.user_id = user_id;
    }


    /**
     * Gets the platform value for this ProfileData.
     * 
     * @return platform
     */
    public java.lang.String getPlatform() {
        return platform;
    }


    /**
     * Sets the platform value for this ProfileData.
     * 
     * @param platform
     */
    public void setPlatform(java.lang.String platform) {
        this.platform = platform;
    }


    /**
     * Gets the os value for this ProfileData.
     * 
     * @return os
     */
    public java.lang.String getOs() {
        return os;
    }


    /**
     * Sets the os value for this ProfileData.
     * 
     * @param os
     */
    public void setOs(java.lang.String os) {
        this.os = os;
    }


    /**
     * Gets the os_build value for this ProfileData.
     * 
     * @return os_build
     */
    public java.lang.String getOs_build() {
        return os_build;
    }


    /**
     * Sets the os_build value for this ProfileData.
     * 
     * @param os_build
     */
    public void setOs_build(java.lang.String os_build) {
        this.os_build = os_build;
    }


    /**
     * Gets the description value for this ProfileData.
     * 
     * @return description
     */
    public java.lang.String getDescription() {
        return description;
    }


    /**
     * Sets the description value for this ProfileData.
     * 
     * @param description
     */
    public void setDescription(java.lang.String description) {
        this.description = description;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ProfileData)) return false;
        ProfileData other = (ProfileData) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.id==null && other.getId()==null) || 
             (this.id!=null &&
              this.id.equals(other.getId()))) &&
            ((this.user_id==null && other.getUser_id()==null) || 
             (this.user_id!=null &&
              this.user_id.equals(other.getUser_id()))) &&
            ((this.platform==null && other.getPlatform()==null) || 
             (this.platform!=null &&
              this.platform.equals(other.getPlatform()))) &&
            ((this.os==null && other.getOs()==null) || 
             (this.os!=null &&
              this.os.equals(other.getOs()))) &&
            ((this.os_build==null && other.getOs_build()==null) || 
             (this.os_build!=null &&
              this.os_build.equals(other.getOs_build()))) &&
            ((this.description==null && other.getDescription()==null) || 
             (this.description!=null &&
              this.description.equals(other.getDescription())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getId() != null) {
            _hashCode += getId().hashCode();
        }
        if (getUser_id() != null) {
            _hashCode += getUser_id().hashCode();
        }
        if (getPlatform() != null) {
            _hashCode += getPlatform().hashCode();
        }
        if (getOs() != null) {
            _hashCode += getOs().hashCode();
        }
        if (getOs_build() != null) {
            _hashCode += getOs_build().hashCode();
        }
        if (getDescription() != null) {
            _hashCode += getDescription().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ProfileData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ProfileData"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("user_id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "user_id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "AccountData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("platform");
        elemField.setXmlName(new javax.xml.namespace.QName("", "platform"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("os");
        elemField.setXmlName(new javax.xml.namespace.QName("", "os"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("os_build");
        elemField.setXmlName(new javax.xml.namespace.QName("", "os_build"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("description");
        elemField.setXmlName(new javax.xml.namespace.QName("", "description"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
