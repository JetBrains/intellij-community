/**
 * RemoteGroup.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteGroup extends RemoteEntity implements java.io.Serializable {
	private String name;

	private RemoteUser[] users;

	public RemoteGroup() {
	}

	public RemoteGroup(
			String name,
			RemoteUser[] users) {
		this.name = name;
		this.users = users;
	}


	/**
	 * Gets the name value for this RemoteGroup.
	 *
	 * @return name
	 */
	public String getName() {
		return name;
	}


	/**
	 * Sets the name value for this RemoteGroup.
	 *
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}


	/**
	 * Gets the users value for this RemoteGroup.
	 *
	 * @return users
	 */
	public RemoteUser[] getUsers() {
		return users;
	}


	/**
	 * Sets the users value for this RemoteGroup.
	 *
	 * @param users
	 */
	public void setUsers(RemoteUser[] users) {
		this.users = users;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteGroup)) {
			return false;
		}
		RemoteGroup other = (RemoteGroup) obj;
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
				((this.name == null && other.getName() == null) ||
						(this.name != null &&
								this.name.equals(other.getName()))) &&
				((this.users == null && other.getUsers() == null) ||
						(this.users != null &&
								java.util.Arrays.equals(this.users, other.getUsers())));
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
		if (getName() != null) {
			_hashCode += getName().hashCode();
		}
		if (getUsers() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getUsers());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getUsers(), i);
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
			new org.apache.axis.description.TypeDesc(RemoteGroup.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteGroup"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("name");
		elemField.setXmlName(new javax.xml.namespace.QName("", "name"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("users");
		elemField.setXmlName(new javax.xml.namespace.QName("", "users"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteUser"));
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
