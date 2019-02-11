/**
 * AttachmentData.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class AttachmentData  implements java.io.Serializable {
    private java.math.BigInteger id;

    private java.lang.String filename;

    private java.math.BigInteger size;

    private java.lang.String content_type;

    private java.util.Calendar date_submitted;

    private org.apache.axis.types.URI download_url;

    private java.math.BigInteger user_id;

    public AttachmentData() {
    }

    public AttachmentData(
           java.math.BigInteger id,
           java.lang.String filename,
           java.math.BigInteger size,
           java.lang.String content_type,
           java.util.Calendar date_submitted,
           org.apache.axis.types.URI download_url,
           java.math.BigInteger user_id) {
           this.id = id;
           this.filename = filename;
           this.size = size;
           this.content_type = content_type;
           this.date_submitted = date_submitted;
           this.download_url = download_url;
           this.user_id = user_id;
    }


    /**
     * Gets the id value for this AttachmentData.
     * 
     * @return id
     */
    public java.math.BigInteger getId() {
        return id;
    }


    /**
     * Sets the id value for this AttachmentData.
     * 
     * @param id
     */
    public void setId(java.math.BigInteger id) {
        this.id = id;
    }


    /**
     * Gets the filename value for this AttachmentData.
     * 
     * @return filename
     */
    public java.lang.String getFilename() {
        return filename;
    }


    /**
     * Sets the filename value for this AttachmentData.
     * 
     * @param filename
     */
    public void setFilename(java.lang.String filename) {
        this.filename = filename;
    }


    /**
     * Gets the size value for this AttachmentData.
     * 
     * @return size
     */
    public java.math.BigInteger getSize() {
        return size;
    }


    /**
     * Sets the size value for this AttachmentData.
     * 
     * @param size
     */
    public void setSize(java.math.BigInteger size) {
        this.size = size;
    }


    /**
     * Gets the content_type value for this AttachmentData.
     * 
     * @return content_type
     */
    public java.lang.String getContent_type() {
        return content_type;
    }


    /**
     * Sets the content_type value for this AttachmentData.
     * 
     * @param content_type
     */
    public void setContent_type(java.lang.String content_type) {
        this.content_type = content_type;
    }


    /**
     * Gets the date_submitted value for this AttachmentData.
     * 
     * @return date_submitted
     */
    public java.util.Calendar getDate_submitted() {
        return date_submitted;
    }


    /**
     * Sets the date_submitted value for this AttachmentData.
     * 
     * @param date_submitted
     */
    public void setDate_submitted(java.util.Calendar date_submitted) {
        this.date_submitted = date_submitted;
    }


    /**
     * Gets the download_url value for this AttachmentData.
     * 
     * @return download_url
     */
    public org.apache.axis.types.URI getDownload_url() {
        return download_url;
    }


    /**
     * Sets the download_url value for this AttachmentData.
     * 
     * @param download_url
     */
    public void setDownload_url(org.apache.axis.types.URI download_url) {
        this.download_url = download_url;
    }


    /**
     * Gets the user_id value for this AttachmentData.
     * 
     * @return user_id
     */
    public java.math.BigInteger getUser_id() {
        return user_id;
    }


    /**
     * Sets the user_id value for this AttachmentData.
     * 
     * @param user_id
     */
    public void setUser_id(java.math.BigInteger user_id) {
        this.user_id = user_id;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof AttachmentData)) return false;
        AttachmentData other = (AttachmentData) obj;
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
            ((this.filename==null && other.getFilename()==null) || 
             (this.filename!=null &&
              this.filename.equals(other.getFilename()))) &&
            ((this.size==null && other.getSize()==null) || 
             (this.size!=null &&
              this.size.equals(other.getSize()))) &&
            ((this.content_type==null && other.getContent_type()==null) || 
             (this.content_type!=null &&
              this.content_type.equals(other.getContent_type()))) &&
            ((this.date_submitted==null && other.getDate_submitted()==null) || 
             (this.date_submitted!=null &&
              this.date_submitted.equals(other.getDate_submitted()))) &&
            ((this.download_url==null && other.getDownload_url()==null) || 
             (this.download_url!=null &&
              this.download_url.equals(other.getDownload_url()))) &&
            ((this.user_id==null && other.getUser_id()==null) || 
             (this.user_id!=null &&
              this.user_id.equals(other.getUser_id())));
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
        if (getFilename() != null) {
            _hashCode += getFilename().hashCode();
        }
        if (getSize() != null) {
            _hashCode += getSize().hashCode();
        }
        if (getContent_type() != null) {
            _hashCode += getContent_type().hashCode();
        }
        if (getDate_submitted() != null) {
            _hashCode += getDate_submitted().hashCode();
        }
        if (getDownload_url() != null) {
            _hashCode += getDownload_url().hashCode();
        }
        if (getUser_id() != null) {
            _hashCode += getUser_id().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(AttachmentData.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "AttachmentData"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("filename");
        elemField.setXmlName(new javax.xml.namespace.QName("", "filename"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("size");
        elemField.setXmlName(new javax.xml.namespace.QName("", "size"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("content_type");
        elemField.setXmlName(new javax.xml.namespace.QName("", "content_type"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("date_submitted");
        elemField.setXmlName(new javax.xml.namespace.QName("", "date_submitted"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("download_url");
        elemField.setXmlName(new javax.xml.namespace.QName("", "download_url"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "anyURI"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("user_id");
        elemField.setXmlName(new javax.xml.namespace.QName("", "user_id"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
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
