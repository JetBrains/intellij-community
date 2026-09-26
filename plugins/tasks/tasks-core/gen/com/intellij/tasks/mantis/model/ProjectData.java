/**
 * ProjectData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class ProjectData  implements java.io.Serializable {
    private java.math.BigInteger id;

    private java.lang.String name;

    private com.intellij.tasks.mantis.model.ObjectRef status;

    private java.lang.Boolean enabled;

    private com.intellij.tasks.mantis.model.ObjectRef view_state;

    private com.intellij.tasks.mantis.model.ObjectRef access_min;

    private java.lang.String file_path;

    private java.lang.String description;

    private com.intellij.tasks.mantis.model.ProjectData[] subprojects;

    private java.lang.Boolean inherit_global;

    public ProjectData() {
    }

    public ProjectData(
           java.math.BigInteger id,
           java.lang.String name,
           com.intellij.tasks.mantis.model.ObjectRef status,
           java.lang.Boolean enabled,
           com.intellij.tasks.mantis.model.ObjectRef view_state,
           com.intellij.tasks.mantis.model.ObjectRef access_min,
           java.lang.String file_path,
           java.lang.String description,
           com.intellij.tasks.mantis.model.ProjectData[] subprojects,
           java.lang.Boolean inherit_global) {
           this.id = id;
           this.name = name;
           this.status = status;
           this.enabled = enabled;
           this.view_state = view_state;
           this.access_min = access_min;
           this.file_path = file_path;
           this.description = description;
           this.subprojects = subprojects;
           this.inherit_global = inherit_global;
    }


    /**
     * Gets the id value for this ProjectData.
     * 
     * @return id
     */
    public java.math.BigInteger getId() {
        return id;
    }


    /**
     * Sets the id value for this ProjectData.
     * 
     * @param id
     */
    public void setId(java.math.BigInteger id) {
        this.id = id;
    }


    /**
     * Gets the name value for this ProjectData.
     * 
     * @return name
     */
    public java.lang.String getName() {
        return name;
    }


    /**
     * Sets the name value for this ProjectData.
     * 
     * @param name
     */
    public void setName(java.lang.String name) {
        this.name = name;
    }


    /**
     * Gets the status value for this ProjectData.
     * 
     * @return status
     */
    public com.intellij.tasks.mantis.model.ObjectRef getStatus() {
        return status;
    }


    /**
     * Sets the status value for this ProjectData.
     * 
     * @param status
     */
    public void setStatus(com.intellij.tasks.mantis.model.ObjectRef status) {
        this.status = status;
    }


    /**
     * Gets the enabled value for this ProjectData.
     * 
     * @return enabled
     */
    public java.lang.Boolean getEnabled() {
        return enabled;
    }


    /**
     * Sets the enabled value for this ProjectData.
     * 
     * @param enabled
     */
    public void setEnabled(java.lang.Boolean enabled) {
        this.enabled = enabled;
    }


    /**
     * Gets the view_state value for this ProjectData.
     * 
     * @return view_state
     */
    public com.intellij.tasks.mantis.model.ObjectRef getView_state() {
        return view_state;
    }


    /**
     * Sets the view_state value for this ProjectData.
     * 
     * @param view_state
     */
    public void setView_state(com.intellij.tasks.mantis.model.ObjectRef view_state) {
        this.view_state = view_state;
    }


    /**
     * Gets the access_min value for this ProjectData.
     * 
     * @return access_min
     */
    public com.intellij.tasks.mantis.model.ObjectRef getAccess_min() {
        return access_min;
    }


    /**
     * Sets the access_min value for this ProjectData.
     * 
     * @param access_min
     */
    public void setAccess_min(com.intellij.tasks.mantis.model.ObjectRef access_min) {
        this.access_min = access_min;
    }


    /**
     * Gets the file_path value for this ProjectData.
     * 
     * @return file_path
     */
    public java.lang.String getFile_path() {
        return file_path;
    }


    /**
     * Sets the file_path value for this ProjectData.
     * 
     * @param file_path
     */
    public void setFile_path(java.lang.String file_path) {
        this.file_path = file_path;
    }


    /**
     * Gets the description value for this ProjectData.
     * 
     * @return description
     */
    public java.lang.String getDescription() {
        return description;
    }


    /**
     * Sets the description value for this ProjectData.
     * 
     * @param description
     */
    public void setDescription(java.lang.String description) {
        this.description = description;
    }


    /**
     * Gets the subprojects value for this ProjectData.
     * 
     * @return subprojects
     */
    public com.intellij.tasks.mantis.model.ProjectData[] getSubprojects() {
        return subprojects;
    }


    /**
     * Sets the subprojects value for this ProjectData.
     * 
     * @param subprojects
     */
    public void setSubprojects(com.intellij.tasks.mantis.model.ProjectData[] subprojects) {
        this.subprojects = subprojects;
    }


    /**
     * Gets the inherit_global value for this ProjectData.
     * 
     * @return inherit_global
     */
    public java.lang.Boolean getInherit_global() {
        return inherit_global;
    }


    /**
     * Sets the inherit_global value for this ProjectData.
     * 
     * @param inherit_global
     */
    public void setInherit_global(java.lang.Boolean inherit_global) {
        this.inherit_global = inherit_global;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ProjectData)) return false;
        ProjectData other = (ProjectData) obj;
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
            ((this.status==null && other.getStatus()==null) || 
             (this.status!=null &&
              this.status.equals(other.getStatus()))) &&
            ((this.enabled==null && other.getEnabled()==null) || 
             (this.enabled!=null &&
              this.enabled.equals(other.getEnabled()))) &&
            ((this.view_state==null && other.getView_state()==null) || 
             (this.view_state!=null &&
              this.view_state.equals(other.getView_state()))) &&
            ((this.access_min==null && other.getAccess_min()==null) || 
             (this.access_min!=null &&
              this.access_min.equals(other.getAccess_min()))) &&
            ((this.file_path==null && other.getFile_path()==null) || 
             (this.file_path!=null &&
              this.file_path.equals(other.getFile_path()))) &&
            ((this.description==null && other.getDescription()==null) || 
             (this.description!=null &&
              this.description.equals(other.getDescription()))) &&
            ((this.subprojects==null && other.getSubprojects()==null) || 
             (this.subprojects!=null &&
              java.util.Arrays.equals(this.subprojects, other.getSubprojects()))) &&
            ((this.inherit_global==null && other.getInherit_global()==null) || 
             (this.inherit_global!=null &&
              this.inherit_global.equals(other.getInherit_global())));
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
        if (getStatus() != null) {
            _hashCode += getStatus().hashCode();
        }
        if (getEnabled() != null) {
            _hashCode += getEnabled().hashCode();
        }
        if (getView_state() != null) {
            _hashCode += getView_state().hashCode();
        }
        if (getAccess_min() != null) {
            _hashCode += getAccess_min().hashCode();
        }
        if (getFile_path() != null) {
            _hashCode += getFile_path().hashCode();
        }
        if (getDescription() != null) {
            _hashCode += getDescription().hashCode();
        }
        if (getSubprojects() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getSubprojects());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getSubprojects(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getInherit_global() != null) {
            _hashCode += getInherit_global().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ProjectData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ProjectData"));
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
        elemField.setFieldName("status");
        elemField.setXmlName(new javax.xml.namespace.QName("", "status"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("enabled");
        elemField.setXmlName(new javax.xml.namespace.QName("", "enabled"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("view_state");
        elemField.setXmlName(new javax.xml.namespace.QName("", "view_state"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("access_min");
        elemField.setXmlName(new javax.xml.namespace.QName("", "access_min"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("file_path");
        elemField.setXmlName(new javax.xml.namespace.QName("", "file_path"));
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
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("subprojects");
        elemField.setXmlName(new javax.xml.namespace.QName("", "subprojects"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ProjectData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("inherit_global");
        elemField.setXmlName(new javax.xml.namespace.QName("", "inherit_global"));
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
