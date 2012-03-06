/**
 * JiraSoapServiceService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

public interface JiraSoapServiceService extends javax.xml.rpc.Service {
	public String getJirasoapserviceV2Address();

	public JiraSoapService getJirasoapserviceV2()
			throws javax.xml.rpc.ServiceException;

	public JiraSoapService getJirasoapserviceV2(java.net.URL portAddress)
			throws javax.xml.rpc.ServiceException;
}
