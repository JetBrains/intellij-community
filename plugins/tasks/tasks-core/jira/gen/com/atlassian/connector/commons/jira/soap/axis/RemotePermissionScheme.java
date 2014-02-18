/**
 * RemotePermissionScheme.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemotePermissionScheme extends RemoteScheme
		implements java.io.Serializable {
	private RemotePermissionMapping[] permissionMappings;

	public RemotePermissionScheme() {
	}

	public RemotePermissionScheme(
			String description,
			Long id,
			String name,
			String type,
			RemotePermissionMapping[] permissionMappings) {
		super(
				description,
				id,
				name,
				type);
		this.permissionMappings = permissionMappings;
	}


	/**
	 * Gets the permissionMappings value for this RemotePermissionScheme.
	 *
	 * @return permissionMappings
	 */
	public RemotePermissionMapping[] getPermissionMappings() {
		return permissionMappings;
	}


	/**
	 * Sets the permissionMappings value for this RemotePermissionScheme.
	 *
	 * @param permissionMappings
	 */
	public void setPermissionMappings(RemotePermissionMapping[] permissionMappings) {
		this.permissionMappings = permissionMappings;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemotePermissionScheme)) {
			return false;
		}
		RemotePermissionScheme other = (RemotePermissionScheme) obj;
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
				((this.permissionMappings == null && other.getPermissionMappings() == null) ||
						(this.permissionMappings != null &&
								java.util.Arrays.equals(this.permissionMappings, other.getPermissionMappings())));
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
		if (getPermissionMappings() != null) {
			for (int i = 0;
				 i < java.lang.reflect.Array.getLength(getPermissionMappings());
				 i++) {
				Object obj = java.lang.reflect.Array.get(getPermissionMappings(), i);
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
			new org.apache.axis.description.TypeDesc(RemotePermissionScheme.class, true);

	static {
		typeDesc.setXmlType(
				new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemotePermissionScheme"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("permissionMappings");
		elemField.setXmlName(new javax.xml.namespace.QName("", "permissionMappings"));
		elemField.setXmlType(
				new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemotePermissionMapping"));
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
