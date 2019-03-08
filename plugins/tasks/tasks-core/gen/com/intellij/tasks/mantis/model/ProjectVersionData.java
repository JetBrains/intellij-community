/**
 * ProjectVersionData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class ProjectVersionData  implements java.io.Serializable {
    private java.math.BigInteger id;

    private java.lang.String name;

    private java.math.BigInteger project_id;

    private java.util.Calendar date_order;

    private java.lang.String description;

    private java.lang.Boolean released;

    private java.lang.Boolean obsolete;

    public ProjectVersionData() {
    }

    public ProjectVersionData(
           java.math.BigInteger id,
           java.lang.String name,
           java.math.BigInteger project_id,
           java.util.Calendar date_order,
           java.lang.String description,
           java.lang.Boolean released,
           java.lang.Boolean obsolete) {
           this.id = id;
           this.name = name;
           this.project_id = project_id;
           this.date_order = date_order;
           this.description = description;
           this.released = released;
           this.obsolete = obsolete;
    }


    /**
     * Gets the id value for this ProjectVersionData.
     * 
     * @return id
     */
    public java.math.BigInteger getId() {
        return id;
    }


    /**
     * Sets the id value for this ProjectVersionData.
     * 
     * @param id
     */
    public void setId(java.math.BigInteger id) {
        this.id = id;
    }


    /**
     * Gets the name value for this ProjectVersionData.
     * 
     * @return name
     */
    public java.lang.String getName() {
        return name;
    }


    /**
     * Sets the name value for this ProjectVersionData.
     * 
     * @param name
     */
    public void setName(java.lang.String name) {
        this.name = name;
    }


    /**
     * Gets the project_id value for this ProjectVersionData.
     * 
     * @return project_id
     */
    public java.math.BigInteger getProject_id() {
        return project_id;
    }


    /**
     * Sets the project_id value for this ProjectVersionData.
     * 
     * @param project_id
     */
    public void setProject_id(java.math.BigInteger project_id) {
        this.project_id = project_id;
    }


    /**
     * Gets the date_order value for this ProjectVersionData.
     * 
     * @return date_order
     */
    public java.util.Calendar getDate_order() {
        return date_order;
    }


    /**
     * Sets the date_order value for this ProjectVersionData.
     * 
     * @param date_order
     */
    public void setDate_order(java.util.Calendar date_order) {
        this.date_order = date_order;
    }


    /**
     * Gets the description value for this ProjectVersionData.
     * 
     * @return description
     */
    public java.lang.String getDescription() {
        return description;
    }


    /**
     * Sets the description value for this ProjectVersionData.
     * 
     * @param description
     */
    public void setDescription(java.lang.String description) {
        this.description = description;
    }


    /**
     * Gets the released value for this ProjectVersionData.
     * 
     * @return released
     */
    public java.lang.Boolean getReleased() {
        return released;
    }


    /**
     * Sets the released value for this ProjectVersionData.
     * 
     * @param released
     */
    public void setReleased(java.lang.Boolean released) {
        this.released = released;
    }


    /**
     * Gets the obsolete value for this ProjectVersionData.
     * 
     * @return obsolete
     */
    public java.lang.Boolean getObsolete() {
        return obsolete;
    }


    /**
     * Sets the obsolete value for this ProjectVersionData.
     * 
     * @param obsolete
     */
    public void setObsolete(java.lang.Boolean obsolete) {
        this.obsolete = obsolete;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ProjectVersionData)) return false;
        ProjectVersionData other = (ProjectVersionData) obj;
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
            ((this.name==null && other.getName()==null) || 
             (this.name!=null &&
              this.name.equals(other.getName()))) &&
            ((this.project_id==null && other.getProject_id()==null) || 
             (this.project_id!=null &&
              this.project_id.equals(other.getProject_id()))) &&
            ((this.date_order==null && other.getDate_order()==null) || 
             (this.date_order!=null &&
              this.date_order.equals(other.getDate_order()))) &&
            ((this.description==null && other.getDescription()==null) || 
             (this.description!=null &&
              this.description.equals(other.getDescription()))) &&
            ((this.released==null && other.getReleased()==null) || 
             (this.released!=null &&
              this.released.equals(other.getReleased()))) &&
            ((this.obsolete==null && other.getObsolete()==null) || 
             (this.obsolete!=null &&
              this.obsolete.equals(other.getObsolete())));
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
        if (getName() != null) {
            _hashCode += getName().hashCode();
        }
        if (getProject_id() != null) {
            _hashCode += getProject_id().hashCode();
        }
        if (getDate_order() != null) {
            _hashCode += getDate_order().hashCode();
        }
        if (getDescription() != null) {
            _hashCode += getDescription().hashCode();
        }
        if (getReleased() != null) {
            _hashCode += getReleased().hashCode();
        }
        if (getObsolete() != null) {
            _hashCode += getObsolete().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ProjectVersionData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ProjectVersionData"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
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
        elemField.setFieldName("project_id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "project_id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("date_order");
        elemField.setXmlName(new javax.xml.namespace.QName("", "date_order"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
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
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("released");
        elemField.setXmlName(new javax.xml.namespace.QName("", "released"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("obsolete");
        elemField.setXmlName(new javax.xml.namespace.QName("", "obsolete"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
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
