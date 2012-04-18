/**
 * JiraSoapServiceServiceLocator.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package com.atlassian.connector.commons.jira.soap.axis;

import org.apache.axis.EngineConfiguration;

public class JiraSoapServiceServiceLocator extends org.apache.axis.client.Service
		implements JiraSoapServiceService {

	public JiraSoapServiceServiceLocator() {
	}


	public JiraSoapServiceServiceLocator(EngineConfiguration config) {
		super(config);
	}

	public JiraSoapServiceServiceLocator(String wsdlLoc, javax.xml.namespace.QName sName)
			throws javax.xml.rpc.ServiceException {
		super(wsdlLoc, sName);
	}

	// Use to get a proxy class for JirasoapserviceV2
	private String JirasoapserviceV2_address = "http://jira.atlassian.com/rpc/soap/jirasoapservice-v2";

	public String getJirasoapserviceV2Address() {
		return JirasoapserviceV2_address;
	}

	// The WSDD service name defaults to the port name.
	private String JirasoapserviceV2WSDDServiceName = "jirasoapservice-v2";

	public String getJirasoapserviceV2WSDDServiceName() {
		return JirasoapserviceV2WSDDServiceName;
	}

	public void setJirasoapserviceV2WSDDServiceName(String name) {
		JirasoapserviceV2WSDDServiceName = name;
	}

	/*
	  overiden configuration from file client-config.wsdd
	* */
	@Override
	protected EngineConfiguration getEngineConfiguration() {
		StringBuffer sb = new StringBuffer();

		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<deployment name=\"defaultClientConfig\"\n"
				+ "            xmlns=\"http://xml.apache.org/axis/wsdd/\"\n"
				+ "            xmlns:java=\"http://xml.apache.org/axis/wsdd/providers/java\">\n"
				+ " <globalConfiguration>\n"
				+ "   <parameter name=\"disablePrettyXML\" value=\"true\"/>\n"
				+ "   <parameter name=\"enableNamespacePrefixOptimization\" value=\"false\"/>\n"
				+ " </globalConfiguration>");

		sb.append("<transport name=\"http\" pivot=\"java:").append(org.apache.axis.transport.http.HTTPSender.class.getName()).append("\" />\r\n");
		sb.append("<transport name=\"local\" pivot=\"java:").append(org.apache.axis.transport.local.LocalSender.class.getName()).append("\" />\r\n");
		sb.append("<transport name=\"java\" pivot=\"java:").append(org.apache.axis.transport.java.JavaSender.class.getName()).append("\" />\r\n");
		sb.append("</deployment>\r\n");
		org.apache.axis.configuration.XMLStringProvider config =
				new org.apache.axis.configuration.XMLStringProvider(sb.toString());
		return config;
	}


	public JiraSoapService getJirasoapserviceV2()
			throws javax.xml.rpc.ServiceException {
		java.net.URL endpoint;
		try {
			endpoint = new java.net.URL(JirasoapserviceV2_address);
		} catch (java.net.MalformedURLException e) {
			throw new javax.xml.rpc.ServiceException(e);
		}
		return getJirasoapserviceV2(endpoint);
	}

	public JiraSoapService getJirasoapserviceV2(java.net.URL portAddress)
			throws javax.xml.rpc.ServiceException {
		try {
			JirasoapserviceV2SoapBindingStub _stub
					= new JirasoapserviceV2SoapBindingStub(portAddress,
					this);
			_stub.setPortName(getJirasoapserviceV2WSDDServiceName());
			return _stub;
		} catch (org.apache.axis.AxisFault e) {
			return null;
		}
	}

	public void setJirasoapserviceV2EndpointAddress(String address) {
		JirasoapserviceV2_address = address;
	}

	/**
	 * For the given interface, get the stub implementation.
	 * If this service has no port for the given interface,
	 * then ServiceException is thrown.
	 */
	public java.rmi.Remote getPort(Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
		try {
			if (JiraSoapService.class
					.isAssignableFrom(serviceEndpointInterface)) {
				JirasoapserviceV2SoapBindingStub _stub
						= new JirasoapserviceV2SoapBindingStub(
						new java.net.URL(JirasoapserviceV2_address), this);
				_stub.setPortName(getJirasoapserviceV2WSDDServiceName());
				return _stub;
			}
		} catch (Throwable t) {
			throw new javax.xml.rpc.ServiceException(t);
		}
		throw new javax.xml.rpc.ServiceException("There is no stub implementation for the interface:  " +
				(serviceEndpointInterface == null ? "null" : serviceEndpointInterface.getName()));
	}

	/**
	 * For the given interface, get the stub implementation.
	 * If this service has no port for the given interface,
	 * then ServiceException is thrown.
	 */
	public java.rmi.Remote getPort(javax.xml.namespace.QName portName, Class serviceEndpointInterface)
			throws javax.xml.rpc.ServiceException {
		if (portName == null) {
			return getPort(serviceEndpointInterface);
		}
		String inputPortName = portName.getLocalPart();
		if ("jirasoapservice-v2".equals(inputPortName)) {
			return getJirasoapserviceV2();
		} else {
			java.rmi.Remote _stub = getPort(serviceEndpointInterface);
			((org.apache.axis.client.Stub) _stub).setPortName(portName);
			return _stub;
		}
	}

	public javax.xml.namespace.QName getServiceName() {
		return new javax.xml.namespace.QName("http://jira.atlassian.com/rpc/soap/jirasoapservice-v2",
				"JiraSoapServiceService");
	}

	private java.util.HashSet ports = null;

	public java.util.Iterator getPorts() {
		if (ports == null) {
			ports = new java.util.HashSet();
			ports.add(new javax.xml.namespace.QName("http://jira.atlassian.com/rpc/soap/jirasoapservice-v2",
					"jirasoapservice-v2"));
		}
		return ports.iterator();
	}

	/**
	 * Set the endpoint address for the specified port name.
	 */
	public void setEndpointAddress(String portName, String address)
			throws javax.xml.rpc.ServiceException {

		if ("JirasoapserviceV2".equals(portName)) {
			setJirasoapserviceV2EndpointAddress(address);
		} else { // Unknown Port Name
			throw new javax.xml.rpc.ServiceException(" Cannot set Endpoint Address for Unknown Port" + portName);
		}
	}

	/**
	 * Set the endpoint address for the specified port name.
	 */
	public void setEndpointAddress(javax.xml.namespace.QName portName, String address)
			throws javax.xml.rpc.ServiceException {
		setEndpointAddress(portName.getLocalPart(), address);
	}

}
