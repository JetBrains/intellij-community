/**
 * FilterData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class FilterData  implements java.io.Serializable {
    private java.math.BigInteger id;

    private com.intellij.tasks.mantis.model.AccountData owner;

    private java.math.BigInteger project_id;

    private java.lang.Boolean is_public;

    private java.lang.String name;

    private java.lang.String filter_string;

    private java.lang.String url;

    public FilterData() {
    }

    public FilterData(
           java.math.BigInteger id,
           com.intellij.tasks.mantis.model.AccountData owner,
           java.math.BigInteger project_id,
           java.lang.Boolean is_public,
           java.lang.String name,
           java.lang.String filter_string,
           java.lang.String url) {
           this.id = id;
           this.owner = owner;
           this.project_id = project_id;
           this.is_public = is_public;
           this.name = name;
           this.filter_string = filter_string;
           this.url = url;
    }


    /**
     * Gets the id value for this FilterData.
     * 
     * @return id
     */
    public java.math.BigInteger getId() {
        return id;
    }


    /**
     * Sets the id value for this FilterData.
     * 
     * @param id
     */
    public void setId(java.math.BigInteger id) {
        this.id = id;
    }


    /**
     * Gets the owner value for this FilterData.
     * 
     * @return owner
     */
    public com.intellij.tasks.mantis.model.AccountData getOwner() {
        return owner;
    }


    /**
     * Sets the owner value for this FilterData.
     * 
     * @param owner
     */
    public void setOwner(com.intellij.tasks.mantis.model.AccountData owner) {
        this.owner = owner;
    }


    /**
     * Gets the project_id value for this FilterData.
     * 
     * @return project_id
     */
    public java.math.BigInteger getProject_id() {
        return project_id;
    }


    /**
     * Sets the project_id value for this FilterData.
     * 
     * @param project_id
     */
    public void setProject_id(java.math.BigInteger project_id) {
        this.project_id = project_id;
    }


    /**
     * Gets the is_public value for this FilterData.
     * 
     * @return is_public
     */
    public java.lang.Boolean getIs_public() {
        return is_public;
    }


    /**
     * Sets the is_public value for this FilterData.
     * 
     * @param is_public
     */
    public void setIs_public(java.lang.Boolean is_public) {
        this.is_public = is_public;
    }


    /**
     * Gets the name value for this FilterData.
     * 
     * @return name
     */
    public java.lang.String getName() {
        return name;
    }


    /**
     * Sets the name value for this FilterData.
     * 
     * @param name
     */
    public void setName(java.lang.String name) {
        this.name = name;
    }


    /**
     * Gets the filter_string value for this FilterData.
     * 
     * @return filter_string
     */
    public java.lang.String getFilter_string() {
        return filter_string;
    }


    /**
     * Sets the filter_string value for this FilterData.
     * 
     * @param filter_string
     */
    public void setFilter_string(java.lang.String filter_string) {
        this.filter_string = filter_string;
    }


    /**
     * Gets the url value for this FilterData.
     * 
     * @return url
     */
    public java.lang.String getUrl() {
        return url;
    }


    /**
     * Sets the url value for this FilterData.
     * 
     * @param url
     */
    public void setUrl(java.lang.String url) {
        this.url = url;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof FilterData)) return false;
        FilterData other = (FilterData) obj;
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
            ((this.owner==null && other.getOwner()==null) || 
             (this.owner!=null &&
              this.owner.equals(other.getOwner()))) &&
            ((this.project_id==null && other.getProject_id()==null) || 
             (this.project_id!=null &&
              this.project_id.equals(other.getProject_id()))) &&
            ((this.is_public==null && other.getIs_public()==null) || 
             (this.is_public!=null &&
              this.is_public.equals(other.getIs_public()))) &&
            ((this.name==null && other.getName()==null) || 
             (this.name!=null &&
              this.name.equals(other.getName()))) &&
            ((this.filter_string==null && other.getFilter_string()==null) || 
             (this.filter_string!=null &&
              this.filter_string.equals(other.getFilter_string()))) &&
            ((this.url==null && other.getUrl()==null) || 
             (this.url!=null &&
              this.url.equals(other.getUrl())));
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
        if (getOwner() != null) {
            _hashCode += getOwner().hashCode();
        }
        if (getProject_id() != null) {
            _hashCode += getProject_id().hashCode();
        }
        if (getIs_public() != null) {
            _hashCode += getIs_public().hashCode();
        }
        if (getName() != null) {
            _hashCode += getName().hashCode();
        }
        if (getFilter_string() != null) {
            _hashCode += getFilter_string().hashCode();
        }
        if (getUrl() != null) {
            _hashCode += getUrl().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(FilterData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "FilterData"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("owner");
        elemField.setXmlName(new javax.xml.namespace.QName("", "owner"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "AccountData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("project_id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "project_id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("is_public");
        elemField.setXmlName(new javax.xml.namespace.QName("", "is_public"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("name");
        elemField.setXmlName(new javax.xml.namespace.QName("", "name"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("filter_string");
        elemField.setXmlName(new javax.xml.namespace.QName("", "filter_string"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("url");
        elemField.setXmlName(new javax.xml.namespace.QName("", "url"));
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
