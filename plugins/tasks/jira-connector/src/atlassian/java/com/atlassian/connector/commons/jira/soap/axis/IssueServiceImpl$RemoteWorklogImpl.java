/**
 * IssueServiceImpl$RemoteWorklogImpl.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;


public class IssueServiceImpl$RemoteWorklogImpl extends RemoteWorklog
		implements java.io.Serializable {
	public IssueServiceImpl$RemoteWorklogImpl() {
	}

	public IssueServiceImpl$RemoteWorklogImpl(
			String author,
			String comment,
			java.util.Calendar created,
			String groupLevel,
			String id,
			String roleLevelId,
			java.util.Calendar startDate,
			String timeSpent,
			long timeSpentInSeconds,
			String updateAuthor,
			java.util.Calendar updated) {
		super(
				author,
				comment,
				created,
				groupLevel,
				id,
				roleLevelId,
				startDate,
				timeSpent,
				timeSpentInSeconds,
				updateAuthor,
				updated);
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof IssueServiceImpl$RemoteWorklogImpl)) {
			return false;
		}
		IssueServiceImpl$RemoteWorklogImpl other = (IssueServiceImpl$RemoteWorklogImpl) obj;
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
		_equals = super.equals(obj);
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
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(IssueServiceImpl$RemoteWorklogImpl.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://service.soap.rpc.jira.atlassian.com",
				"IssueServiceImpl$RemoteWorklogImpl"));
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
