/**
 * RemoteProjectRole.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteProjectRole implements java.io.Serializable {
	private String description;

	private Long id;

	private String name;

	public RemoteProjectRole() {
	}

	public RemoteProjectRole(
			String description,
			Long id,
			String name) {
		this.description = description;
		this.id = id;
		this.name = name;
	}


	/**
	 * Gets the description value for this RemoteProjectRole.
	 *
	 * @return description
	 */
	public String getDescription() {
		return description;
	}


	/**
	 * Sets the description value for this RemoteProjectRole.
	 *
	 * @param description
	 */
	public void setDescription(String description) {
		this.description = description;
	}


	/**
	 * Gets the id value for this RemoteProjectRole.
	 *
	 * @return id
	 */
	public Long getId() {
		return id;
	}


	/**
	 * Sets the id value for this RemoteProjectRole.
	 *
	 * @param id
	 */
	public void setId(Long id) {
		this.id = id;
	}


	/**
	 * Gets the name value for this RemoteProjectRole.
	 *
	 * @return name
	 */
	public String getName() {
		return name;
	}


	/**
	 * Sets the name value for this RemoteProjectRole.
	 *
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteProjectRole)) {
			return false;
		}
		RemoteProjectRole other = (RemoteProjectRole) obj;
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
				((this.description == null && other.getDescription() == null) ||
						(this.description != null &&
								this.description.equals(other.getDescription()))) &&
				((this.id == null && other.getId() == null) ||
						(this.id != null &&
								this.id.equals(other.getId()))) &&
				((this.name == null && other.getName() == null) ||
						(this.name != null &&
								this.name.equals(other.getName())));
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
		if (getDescription() != null) {
			_hashCode += getDescription().hashCode();
		}
		if (getId() != null) {
			_hashCode += getId().hashCode();
		}
		if (getName() != null) {
			_hashCode += getName().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteProjectRole.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteProjectRole"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("description");
		elemField.setXmlName(new javax.xml.namespace.QName("", "description"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("id");
		elemField.setXmlName(new javax.xml.namespace.QName("", "id"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "long"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("name");
		elemField.setXmlName(new javax.xml.namespace.QName("", "name"));
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
