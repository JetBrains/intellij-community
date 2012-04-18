/**
 * RemoteFieldValue.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteFieldValue implements java.io.Serializable {
	private String id;

	private String[] values;

	public RemoteFieldValue() {
	}

	public RemoteFieldValue(
			String id,
			String[] values) {
		this.id = id;
		this.values = values;
	}


	/**
	 * Gets the id value for this RemoteFieldValue.
	 *
	 * @return id
	 */
	public String getId() {
		return id;
	}


	/**
	 * Sets the id value for this RemoteFieldValue.
	 *
	 * @param id
	 */
	public void setId(String id) {
		this.id = id;
	}


	/**
	 * Gets the values value for this RemoteFieldValue.
	 *
	 * @return values
	 */
	public String[] getValues() {
		return values;
	}


	/**
	 * Sets the values value for this RemoteFieldValue.
	 *
	 * @param values
	 */
	public void setValues(String[] values) {
		this.values = values;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteFieldValue)) {
			return false;
		}
		RemoteFieldValue other = (RemoteFieldValue) obj;
		if (obj == null) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		if (__equalsCalc != null) {
			return (__equalsCalc == obj);
		}
		__equalsCalc = obj;
		boolean _equals;
		_equals = true &&
				((this.id == null && other.getId() == null) ||
						(this.id != null &&
								this.id.equals(other.getId()))) &&
				((this.values == null && other.getValues() == null) ||
						(this.values != null &&
								java.util.Arrays.equals(this.values, other.getValues())));
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
		if (getValues() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getValues());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getValues(), i);
				if (obj != null &&
						!obj.getClass().isArray()) {
					_hashCode += obj.hashCode();
				}
			}
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteFieldValue.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteFieldValue"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("id");
		elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("values");
		elemField.setXmlName(new javax.xml.namespace.QName("", "values"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
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
			String mechType,
			Class _javaType,
			javax.xml.namespace.QName _xmlType) {
		return
				new org.apache.axis.encoding.ser.BeanSerializer(
						_javaType, _xmlType, typeDesc);
	}

	/**
	 * Get Custom Deserializer
	 */
	public static org.apache.axis.encoding.Deserializer getDeserializer(
			String mechType,
			Class _javaType,
			javax.xml.namespace.QName _xmlType) {
		return
				new org.apache.axis.encoding.ser.BeanDeserializer(
						_javaType, _xmlType, typeDesc);
	}

}
