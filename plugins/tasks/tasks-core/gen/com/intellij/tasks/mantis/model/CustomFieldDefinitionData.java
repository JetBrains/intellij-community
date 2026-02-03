/**
 * CustomFieldDefinitionData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class CustomFieldDefinitionData  implements java.io.Serializable {
    private com.intellij.tasks.mantis.model.ObjectRef field;

    private java.math.BigInteger type;

    private java.lang.String possible_values;

    private java.lang.String default_value;

    private java.lang.String valid_regexp;

    private java.math.BigInteger access_level_r;

    private java.math.BigInteger access_level_rw;

    private java.math.BigInteger length_min;

    private java.math.BigInteger length_max;

    private java.lang.Boolean advanced;

    private java.lang.Boolean display_report;

    private java.lang.Boolean display_update;

    private java.lang.Boolean display_resolved;

    private java.lang.Boolean display_closed;

    private java.lang.Boolean require_report;

    private java.lang.Boolean require_update;

    private java.lang.Boolean require_resolved;

    private java.lang.Boolean require_closed;

    public CustomFieldDefinitionData() {
    }

    public CustomFieldDefinitionData(
           com.intellij.tasks.mantis.model.ObjectRef field,
           java.math.BigInteger type,
           java.lang.String possible_values,
           java.lang.String default_value,
           java.lang.String valid_regexp,
           java.math.BigInteger access_level_r,
           java.math.BigInteger access_level_rw,
           java.math.BigInteger length_min,
           java.math.BigInteger length_max,
           java.lang.Boolean advanced,
           java.lang.Boolean display_report,
           java.lang.Boolean display_update,
           java.lang.Boolean display_resolved,
           java.lang.Boolean display_closed,
           java.lang.Boolean require_report,
           java.lang.Boolean require_update,
           java.lang.Boolean require_resolved,
           java.lang.Boolean require_closed) {
           this.field = field;
           this.type = type;
           this.possible_values = possible_values;
           this.default_value = default_value;
           this.valid_regexp = valid_regexp;
           this.access_level_r = access_level_r;
           this.access_level_rw = access_level_rw;
           this.length_min = length_min;
           this.length_max = length_max;
           this.advanced = advanced;
           this.display_report = display_report;
           this.display_update = display_update;
           this.display_resolved = display_resolved;
           this.display_closed = display_closed;
           this.require_report = require_report;
           this.require_update = require_update;
           this.require_resolved = require_resolved;
           this.require_closed = require_closed;
    }


    /**
     * Gets the field value for this CustomFieldDefinitionData.
     * 
     * @return field
     */
    public com.intellij.tasks.mantis.model.ObjectRef getField() {
        return field;
    }


    /**
     * Sets the field value for this CustomFieldDefinitionData.
     * 
     * @param field
     */
    public void setField(com.intellij.tasks.mantis.model.ObjectRef field) {
        this.field = field;
    }


    /**
     * Gets the type value for this CustomFieldDefinitionData.
     * 
     * @return type
     */
    public java.math.BigInteger getType() {
        return type;
    }


    /**
     * Sets the type value for this CustomFieldDefinitionData.
     * 
     * @param type
     */
    public void setType(java.math.BigInteger type) {
        this.type = type;
    }


    /**
     * Gets the possible_values value for this CustomFieldDefinitionData.
     * 
     * @return possible_values
     */
    public java.lang.String getPossible_values() {
        return possible_values;
    }


    /**
     * Sets the possible_values value for this CustomFieldDefinitionData.
     * 
     * @param possible_values
     */
    public void setPossible_values(java.lang.String possible_values) {
        this.possible_values = possible_values;
    }


    /**
     * Gets the default_value value for this CustomFieldDefinitionData.
     * 
     * @return default_value
     */
    public java.lang.String getDefault_value() {
        return default_value;
    }


    /**
     * Sets the default_value value for this CustomFieldDefinitionData.
     * 
     * @param default_value
     */
    public void setDefault_value(java.lang.String default_value) {
        this.default_value = default_value;
    }


    /**
     * Gets the valid_regexp value for this CustomFieldDefinitionData.
     * 
     * @return valid_regexp
     */
    public java.lang.String getValid_regexp() {
        return valid_regexp;
    }


    /**
     * Sets the valid_regexp value for this CustomFieldDefinitionData.
     * 
     * @param valid_regexp
     */
    public void setValid_regexp(java.lang.String valid_regexp) {
        this.valid_regexp = valid_regexp;
    }


    /**
     * Gets the access_level_r value for this CustomFieldDefinitionData.
     * 
     * @return access_level_r
     */
    public java.math.BigInteger getAccess_level_r() {
        return access_level_r;
    }


    /**
     * Sets the access_level_r value for this CustomFieldDefinitionData.
     * 
     * @param access_level_r
     */
    public void setAccess_level_r(java.math.BigInteger access_level_r) {
        this.access_level_r = access_level_r;
    }


    /**
     * Gets the access_level_rw value for this CustomFieldDefinitionData.
     * 
     * @return access_level_rw
     */
    public java.math.BigInteger getAccess_level_rw() {
        return access_level_rw;
    }


    /**
     * Sets the access_level_rw value for this CustomFieldDefinitionData.
     * 
     * @param access_level_rw
     */
    public void setAccess_level_rw(java.math.BigInteger access_level_rw) {
        this.access_level_rw = access_level_rw;
    }


    /**
     * Gets the length_min value for this CustomFieldDefinitionData.
     * 
     * @return length_min
     */
    public java.math.BigInteger getLength_min() {
        return length_min;
    }


    /**
     * Sets the length_min value for this CustomFieldDefinitionData.
     * 
     * @param length_min
     */
    public void setLength_min(java.math.BigInteger length_min) {
        this.length_min = length_min;
    }


    /**
     * Gets the length_max value for this CustomFieldDefinitionData.
     * 
     * @return length_max
     */
    public java.math.BigInteger getLength_max() {
        return length_max;
    }


    /**
     * Sets the length_max value for this CustomFieldDefinitionData.
     * 
     * @param length_max
     */
    public void setLength_max(java.math.BigInteger length_max) {
        this.length_max = length_max;
    }


    /**
     * Gets the advanced value for this CustomFieldDefinitionData.
     * 
     * @return advanced
     */
    public java.lang.Boolean getAdvanced() {
        return advanced;
    }


    /**
     * Sets the advanced value for this CustomFieldDefinitionData.
     * 
     * @param advanced
     */
    public void setAdvanced(java.lang.Boolean advanced) {
        this.advanced = advanced;
    }


    /**
     * Gets the display_report value for this CustomFieldDefinitionData.
     * 
     * @return display_report
     */
    public java.lang.Boolean getDisplay_report() {
        return display_report;
    }


    /**
     * Sets the display_report value for this CustomFieldDefinitionData.
     * 
     * @param display_report
     */
    public void setDisplay_report(java.lang.Boolean display_report) {
        this.display_report = display_report;
    }


    /**
     * Gets the display_update value for this CustomFieldDefinitionData.
     * 
     * @return display_update
     */
    public java.lang.Boolean getDisplay_update() {
        return display_update;
    }


    /**
     * Sets the display_update value for this CustomFieldDefinitionData.
     * 
     * @param display_update
     */
    public void setDisplay_update(java.lang.Boolean display_update) {
        this.display_update = display_update;
    }


    /**
     * Gets the display_resolved value for this CustomFieldDefinitionData.
     * 
     * @return display_resolved
     */
    public java.lang.Boolean getDisplay_resolved() {
        return display_resolved;
    }


    /**
     * Sets the display_resolved value for this CustomFieldDefinitionData.
     * 
     * @param display_resolved
     */
    public void setDisplay_resolved(java.lang.Boolean display_resolved) {
        this.display_resolved = display_resolved;
    }


    /**
     * Gets the display_closed value for this CustomFieldDefinitionData.
     * 
     * @return display_closed
     */
    public java.lang.Boolean getDisplay_closed() {
        return display_closed;
    }


    /**
     * Sets the display_closed value for this CustomFieldDefinitionData.
     * 
     * @param display_closed
     */
    public void setDisplay_closed(java.lang.Boolean display_closed) {
        this.display_closed = display_closed;
    }


    /**
     * Gets the require_report value for this CustomFieldDefinitionData.
     * 
     * @return require_report
     */
    public java.lang.Boolean getRequire_report() {
        return require_report;
    }


    /**
     * Sets the require_report value for this CustomFieldDefinitionData.
     * 
     * @param require_report
     */
    public void setRequire_report(java.lang.Boolean require_report) {
        this.require_report = require_report;
    }


    /**
     * Gets the require_update value for this CustomFieldDefinitionData.
     * 
     * @return require_update
     */
    public java.lang.Boolean getRequire_update() {
        return require_update;
    }


    /**
     * Sets the require_update value for this CustomFieldDefinitionData.
     * 
     * @param require_update
     */
    public void setRequire_update(java.lang.Boolean require_update) {
        this.require_update = require_update;
    }


    /**
     * Gets the require_resolved value for this CustomFieldDefinitionData.
     * 
     * @return require_resolved
     */
    public java.lang.Boolean getRequire_resolved() {
        return require_resolved;
    }


    /**
     * Sets the require_resolved value for this CustomFieldDefinitionData.
     * 
     * @param require_resolved
     */
    public void setRequire_resolved(java.lang.Boolean require_resolved) {
        this.require_resolved = require_resolved;
    }


    /**
     * Gets the require_closed value for this CustomFieldDefinitionData.
     * 
     * @return require_closed
     */
    public java.lang.Boolean getRequire_closed() {
        return require_closed;
    }


    /**
     * Sets the require_closed value for this CustomFieldDefinitionData.
     * 
     * @param require_closed
     */
    public void setRequire_closed(java.lang.Boolean require_closed) {
        this.require_closed = require_closed;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof CustomFieldDefinitionData)) return false;
        CustomFieldDefinitionData other = (CustomFieldDefinitionData) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.field==null && other.getField()==null) || 
             (this.field!=null &&
              this.field.equals(other.getField()))) &&
            ((this.type==null && other.getType()==null) || 
             (this.type!=null &&
              this.type.equals(other.getType()))) &&
            ((this.possible_values==null && other.getPossible_values()==null) || 
             (this.possible_values!=null &&
              this.possible_values.equals(other.getPossible_values()))) &&
            ((this.default_value==null && other.getDefault_value()==null) || 
             (this.default_value!=null &&
              this.default_value.equals(other.getDefault_value()))) &&
            ((this.valid_regexp==null && other.getValid_regexp()==null) || 
             (this.valid_regexp!=null &&
              this.valid_regexp.equals(other.getValid_regexp()))) &&
            ((this.access_level_r==null && other.getAccess_level_r()==null) || 
             (this.access_level_r!=null &&
              this.access_level_r.equals(other.getAccess_level_r()))) &&
            ((this.access_level_rw==null && other.getAccess_level_rw()==null) || 
             (this.access_level_rw!=null &&
              this.access_level_rw.equals(other.getAccess_level_rw()))) &&
            ((this.length_min==null && other.getLength_min()==null) || 
             (this.length_min!=null &&
              this.length_min.equals(other.getLength_min()))) &&
            ((this.length_max==null && other.getLength_max()==null) || 
             (this.length_max!=null &&
              this.length_max.equals(other.getLength_max()))) &&
            ((this.advanced==null && other.getAdvanced()==null) || 
             (this.advanced!=null &&
              this.advanced.equals(other.getAdvanced()))) &&
            ((this.display_report==null && other.getDisplay_report()==null) || 
             (this.display_report!=null &&
              this.display_report.equals(other.getDisplay_report()))) &&
            ((this.display_update==null && other.getDisplay_update()==null) || 
             (this.display_update!=null &&
              this.display_update.equals(other.getDisplay_update()))) &&
            ((this.display_resolved==null && other.getDisplay_resolved()==null) || 
             (this.display_resolved!=null &&
              this.display_resolved.equals(other.getDisplay_resolved()))) &&
            ((this.display_closed==null && other.getDisplay_closed()==null) || 
             (this.display_closed!=null &&
              this.display_closed.equals(other.getDisplay_closed()))) &&
            ((this.require_report==null && other.getRequire_report()==null) || 
             (this.require_report!=null &&
              this.require_report.equals(other.getRequire_report()))) &&
            ((this.require_update==null && other.getRequire_update()==null) || 
             (this.require_update!=null &&
              this.require_update.equals(other.getRequire_update()))) &&
            ((this.require_resolved==null && other.getRequire_resolved()==null) || 
             (this.require_resolved!=null &&
              this.require_resolved.equals(other.getRequire_resolved()))) &&
            ((this.require_closed==null && other.getRequire_closed()==null) || 
             (this.require_closed!=null &&
              this.require_closed.equals(other.getRequire_closed())));
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
        if (getField() != null) {
            _hashCode += getField().hashCode();
        }
        if (getType() != null) {
            _hashCode += getType().hashCode();
        }
        if (getPossible_values() != null) {
            _hashCode += getPossible_values().hashCode();
        }
        if (getDefault_value() != null) {
            _hashCode += getDefault_value().hashCode();
        }
        if (getValid_regexp() != null) {
            _hashCode += getValid_regexp().hashCode();
        }
        if (getAccess_level_r() != null) {
            _hashCode += getAccess_level_r().hashCode();
        }
        if (getAccess_level_rw() != null) {
            _hashCode += getAccess_level_rw().hashCode();
        }
        if (getLength_min() != null) {
            _hashCode += getLength_min().hashCode();
        }
        if (getLength_max() != null) {
            _hashCode += getLength_max().hashCode();
        }
        if (getAdvanced() != null) {
            _hashCode += getAdvanced().hashCode();
        }
        if (getDisplay_report() != null) {
            _hashCode += getDisplay_report().hashCode();
        }
        if (getDisplay_update() != null) {
            _hashCode += getDisplay_update().hashCode();
        }
        if (getDisplay_resolved() != null) {
            _hashCode += getDisplay_resolved().hashCode();
        }
        if (getDisplay_closed() != null) {
            _hashCode += getDisplay_closed().hashCode();
        }
        if (getRequire_report() != null) {
            _hashCode += getRequire_report().hashCode();
        }
        if (getRequire_update() != null) {
            _hashCode += getRequire_update().hashCode();
        }
        if (getRequire_resolved() != null) {
            _hashCode += getRequire_resolved().hashCode();
        }
        if (getRequire_closed() != null) {
            _hashCode += getRequire_closed().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(CustomFieldDefinitionData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "CustomFieldDefinitionData"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("field");
        elemField.setXmlName(new javax.xml.namespace.QName("", "field"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ObjectRef"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("type");
        elemField.setXmlName(new javax.xml.namespace.QName("", "type"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("possible_values");
        elemField.setXmlName(new javax.xml.namespace.QName("", "possible_values"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("default_value");
        elemField.setXmlName(new javax.xml.namespace.QName("", "default_value"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("valid_regexp");
        elemField.setXmlName(new javax.xml.namespace.QName("", "valid_regexp"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("access_level_r");
        elemField.setXmlName(new javax.xml.namespace.QName("", "access_level_r"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("access_level_rw");
        elemField.setXmlName(new javax.xml.namespace.QName("", "access_level_rw"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("length_min");
        elemField.setXmlName(new javax.xml.namespace.QName("", "length_min"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("length_max");
        elemField.setXmlName(new javax.xml.namespace.QName("", "length_max"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("advanced");
        elemField.setXmlName(new javax.xml.namespace.QName("", "advanced"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("display_report");
        elemField.setXmlName(new javax.xml.namespace.QName("", "display_report"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("display_update");
        elemField.setXmlName(new javax.xml.namespace.QName("", "display_update"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("display_resolved");
        elemField.setXmlName(new javax.xml.namespace.QName("", "display_resolved"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("display_closed");
        elemField.setXmlName(new javax.xml.namespace.QName("", "display_closed"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("require_report");
        elemField.setXmlName(new javax.xml.namespace.QName("", "require_report"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("require_update");
        elemField.setXmlName(new javax.xml.namespace.QName("", "require_update"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("require_resolved");
        elemField.setXmlName(new javax.xml.namespace.QName("", "require_resolved"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("require_closed");
        elemField.setXmlName(new javax.xml.namespace.QName("", "require_closed"));
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
