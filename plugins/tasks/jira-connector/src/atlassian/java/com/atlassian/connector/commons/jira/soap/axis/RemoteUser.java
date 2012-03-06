/**
 * RemoteUser.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteUser extends RemoteEntity implements java.io.Serializable {
	private String email;

	private String fullname;

	private String name;

	public RemoteUser() {
	}

	public RemoteUser(
			String email,
			String fullname,
			String name) {
		this.email = email;
		this.fullname = fullname;
		this.name = name;
	}


	/**
	 * Gets the email value for this RemoteUser.
	 *
	 * @return email
	 */
	public String getEmail() {
		return email;
	}


	/**
	 * Sets the email value for this RemoteUser.
	 *
	 * @param email
	 */
	public void setEmail(String email) {
		this.email = email;
	}


	/**
	 * Gets the fullname value for this RemoteUser.
	 *
	 * @return fullname
	 */
	public String getFullname() {
		return fullname;
	}


	/**
	 * Sets the fullname value for this RemoteUser.
	 *
	 * @param fullname
	 */
	public void setFullname(String fullname) {
		this.fullname = fullname;
	}


	/**
	 * Gets the name value for this RemoteUser.
	 *
	 * @return name
	 */
	public String getName() {
		return name;
	}


	/**
	 * Sets the name value for this RemoteUser.
	 *
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteUser)) {
			return false;
		}
		RemoteUser other = (RemoteUser) obj;
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
		_equals = super.equals(obj) &&
				((this.email == null && other.getEmail() == null) ||
						(this.email != null &&
								this.email.equals(other.getEmail()))) &&
				((this.fullname == null && other.getFullname() == null) ||
						(this.fullname != null &&
								this.fullname.equals(other.getFullname()))) &&
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
		int _hashCode = super.hashCode();
		if (getEmail() != null) {
			_hashCode += getEmail().hashCode();
		}
		if (getFullname() != null) {
			_hashCode += getFullname().hashCode();
		}
		if (getName() != null) {
			_hashCode += getName().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteUser.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteUser"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("email");
		elemField.setXmlName(new javax.xml.namespace.QName("", "email"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("fullname");
		elemField.setXmlName(new javax.xml.namespace.QName("", "fullname"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
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
