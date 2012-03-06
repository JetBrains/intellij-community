/**
 * RemoteVersion.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public class RemoteVersion extends AbstractNamedRemoteEntity
		implements java.io.Serializable {
	private boolean archived;

	private java.util.Calendar releaseDate;

	private boolean released;

	private Long sequence;

	public RemoteVersion() {
	}

	public RemoteVersion(
			String id,
			String name,
			boolean archived,
			java.util.Calendar releaseDate,
			boolean released,
			Long sequence) {
		super(
				id,
				name);
		this.archived = archived;
		this.releaseDate = releaseDate;
		this.released = released;
		this.sequence = sequence;
	}


	/**
	 * Gets the archived value for this RemoteVersion.
	 *
	 * @return archived
	 */
	public boolean isArchived() {
		return archived;
	}


	/**
	 * Sets the archived value for this RemoteVersion.
	 *
	 * @param archived
	 */
	public void setArchived(boolean archived) {
		this.archived = archived;
	}


	/**
	 * Gets the releaseDate value for this RemoteVersion.
	 *
	 * @return releaseDate
	 */
	public java.util.Calendar getReleaseDate() {
		return releaseDate;
	}


	/**
	 * Sets the releaseDate value for this RemoteVersion.
	 *
	 * @param releaseDate
	 */
	public void setReleaseDate(java.util.Calendar releaseDate) {
		this.releaseDate = releaseDate;
	}


	/**
	 * Gets the released value for this RemoteVersion.
	 *
	 * @return released
	 */
	public boolean isReleased() {
		return released;
	}


	/**
	 * Sets the released value for this RemoteVersion.
	 *
	 * @param released
	 */
	public void setReleased(boolean released) {
		this.released = released;
	}


	/**
	 * Gets the sequence value for this RemoteVersion.
	 *
	 * @return sequence
	 */
	public Long getSequence() {
		return sequence;
	}


	/**
	 * Sets the sequence value for this RemoteVersion.
	 *
	 * @param sequence
	 */
	public void setSequence(Long sequence) {
		this.sequence = sequence;
	}

	private Object __equalsCalc = null;

	public synchronized boolean equals(Object obj) {
		if (!(obj instanceof RemoteVersion)) {
			return false;
		}
		RemoteVersion other = (RemoteVersion) obj;
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
				this.archived == other.isArchived() &&
				((this.releaseDate == null && other.getReleaseDate() == null) ||
						(this.releaseDate != null &&
								this.releaseDate.equals(other.getReleaseDate()))) &&
				this.released == other.isReleased() &&
				((this.sequence == null && other.getSequence() == null) ||
						(this.sequence != null &&
								this.sequence.equals(other.getSequence())));
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
		_hashCode += (isArchived() ? Boolean.TRUE : Boolean.FALSE).hashCode();
		if (getReleaseDate() != null) {
			_hashCode += getReleaseDate().hashCode();
		}
		_hashCode += (isReleased() ? Boolean.TRUE : Boolean.FALSE).hashCode();
		if (getSequence() != null) {
			_hashCode += getSequence().hashCode();
		}
		__hashCodeCalc = false;
		return _hashCode;
	}

	// Type metadata
	private static org.apache.axis.description.TypeDesc typeDesc =
			new org.apache.axis.description.TypeDesc(RemoteVersion.class, true);

	static {
		typeDesc.setXmlType(new javax.xml.namespace.QName("http://beans.soap.rpc.jira.atlassian.com", "RemoteVersion"));
		org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("archived");
		elemField.setXmlName(new javax.xml.namespace.QName("", "archived"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
		elemField.setNillable(false);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("releaseDate");
		elemField.setXmlName(new javax.xml.namespace.QName("", "releaseDate"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "dateTime"));
		elemField.setNillable(true);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("released");
		elemField.setXmlName(new javax.xml.namespace.QName("", "released"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
		elemField.setNillable(false);
		typeDesc.addFieldDesc(elemField);
		elemField = new org.apache.axis.description.ElementDesc();
		elemField.setFieldName("sequence");
		elemField.setXmlName(new javax.xml.namespace.QName("", "sequence"));
		elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "long"));
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
