/**
 * RemoteTimeInfo.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteTimeInfo implements java.io.Serializable {
	private String serverTime;

	private String timeZoneId;

	public RemoteTimeInfo() {
	}

	public RemoteTimeInfo(
			String serverTime,
			String timeZoneId) {
		this.serverTime = serverTime;
		this.timeZoneId = timeZoneId;
	}


	/**
	 * Gets the serverTime value for this RemoteTimeInfo.
	 *
	 * @return serverTime
	 */
	public String getServerTime() {
		return serverTime;
	}


	/**
	 * Sets the serverTime value for this RemoteTimeInfo.
	 *
	 * @param serverTime
	 */
	public void setServerTime(String serverTime) {
		this.serverTime = serverTime;
	}


	/**
	 * Gets the timeZoneId value for this RemoteTimeInfo.
	 *
	 * @return timeZoneId
	 */
	public String getTimeZoneId() {
		return timeZoneId;
	}


	/**
	 * Sets the timeZoneId value for this RemoteTimeInfo.
	 *
	 * @param timeZoneId
	 */
	public void setTimeZoneId(String timeZoneId) {
		this.timeZoneId = timeZoneId;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteTimeInfo)) {
			return false;
		}
		RemoteTimeInfo other = (RemoteTimeInfo) obj;
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
				((this.serverTime == null && other.getServerTime() == null) ||
						(this.serverTime != null &&
								this.serverTime.equals(other.getServerTime()))) &&
				((this.timeZoneId == null && other.getTimeZoneId() == null) ||
						(this.timeZoneId != null &&
								this.timeZoneId.equals(other.getTimeZoneId())));
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
		if (getServerTime() != null) {
			_hashCode += getServerTime().hashCode();
		}
		if (getTimeZoneId() != null) {
			_hashCode += getTimeZoneId().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteTimeInfo.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteTimeInfo"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("serverTime");
		elemField.setXmlName(new javax.xml.namespace.QName("", "serverTime"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("timeZoneId");
		elemField.setXmlName(new javax.xml.namespace.QName("", "timeZoneId"));
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
