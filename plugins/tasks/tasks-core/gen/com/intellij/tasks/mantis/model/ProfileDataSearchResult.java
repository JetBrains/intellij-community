/**
 * ProfileDataSearchResult.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.intellij.tasks.mantis.model;

public class ProfileDataSearchResult  implements java.io.Serializable {
    private com.intellij.tasks.mantis.model.ProfileData[] results;

    private java.math.BigInteger total_results;

    public ProfileDataSearchResult() {
    }

    public ProfileDataSearchResult(
           com.intellij.tasks.mantis.model.ProfileData[] results,
           java.math.BigInteger total_results) {
           this.results = results;
           this.total_results = total_results;
    }


    /**
     * Gets the results value for this ProfileDataSearchResult.
     * 
     * @return results
     */
    public com.intellij.tasks.mantis.model.ProfileData[] getResults() {
        return results;
    }


    /**
     * Sets the results value for this ProfileDataSearchResult.
     * 
     * @param results
     */
    public void setResults(com.intellij.tasks.mantis.model.ProfileData[] results) {
        this.results = results;
    }


    /**
     * Gets the total_results value for this ProfileDataSearchResult.
     * 
     * @return total_results
     */
    public java.math.BigInteger getTotal_results() {
        return total_results;
    }


    /**
     * Sets the total_results value for this ProfileDataSearchResult.
     * 
     * @param total_results
     */
    public void setTotal_results(java.math.BigInteger total_results) {
        this.total_results = total_results;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ProfileDataSearchResult)) return false;
        ProfileDataSearchResult other = (ProfileDataSearchResult) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.results==null && other.getResults()==null) || 
             (this.results!=null &&
              java.util.Arrays.equals(this.results, other.getResults()))) &&
            ((this.total_results==null && other.getTotal_results()==null) || 
             (this.total_results!=null &&
              this.total_results.equals(other.getTotal_results())));
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
        if (getResults() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getResults());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getResults(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getTotal_results() != null) {
            _hashCode += getTotal_results().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ProfileDataSearchResult.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ProfileDataSearchResult"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("results");
        elemField.setXmlName(new javax.xml.namespace.QName("", "results"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://futureware.biz/mantisconnect", "ProfileData"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("total_results");
        elemField.setXmlName(new javax.xml.namespace.QName("", "total_results"));
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
