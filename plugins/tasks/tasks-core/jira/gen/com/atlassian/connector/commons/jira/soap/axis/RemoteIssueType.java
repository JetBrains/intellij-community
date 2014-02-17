/**
 * RemoteIssueType.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteIssueType extends AbstractRemoteConstant
		implements java.io.Serializable {
	private boolean subTask;

	public RemoteIssueType() {
	}

	public RemoteIssueType(
			String id,
			String name,
			String description,
			String icon,
			boolean subTask) {
		super(
				id,
				name,
				description,
				icon);
		this.subTask = subTask;
	}


	/**
	 * Gets the subTask value for this RemoteIssueType.
	 *
	 * @return subTask
	 */
	public boolean isSubTask() {
		return subTask;
	}


	/**
	 * Sets the subTask value for this RemoteIssueType.
	 *
	 * @param subTask
	 */
	public void setSubTask(boolean subTask) {
		this.subTask = subTask;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteIssueType)) {
			return false;
		}
		RemoteIssueType other = (RemoteIssueType) obj;
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
				this.subTask == other.isSubTask();
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
		_hashCode += (isSubTask() ? Boolean.TRUE : Boolean.FALSE).hashCode();
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteIssueType.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteIssueType"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("subTask");
		elemField.setXmlName(new javax.xml.namespace.QName("", "subTask"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
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
