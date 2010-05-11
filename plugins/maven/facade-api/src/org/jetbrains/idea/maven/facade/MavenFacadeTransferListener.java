package org.jetbrains.idea.maven.facade;

import java.io.File;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Gregory.Shrago
 */
public interface MavenFacadeTransferListener extends Remote {
  void transferInitiated(TransferEvent transferEvent) throws RemoteException;

  void transferStarted(TransferEvent transferEvent) throws RemoteException;

  void transferProgress(TransferEvent transferEvent, int length) throws RemoteException;

  void transferCompleted(TransferEvent transferEvent) throws RemoteException;

  void transferError(TransferEvent transferEvent) throws RemoteException;

  void debug(String s) throws RemoteException;

  class TransferEvent implements Serializable {
    public enum EventType { INITIATED, STARTED, COMPLETED, PROGRESS, ERROR }
    public enum RequestType { PUT, GET }

    private final String myRepositoryUrl;
    private final File myLocalFile;
    private final EventType myEventType;
    private final RequestType myRequestType;
    private final Resource myResource;
    private final Exception myException;

    public TransferEvent(File localFile,
                         EventType eventType,
                         RequestType requestType,
                         Resource resource,
                         Exception exception,
                         String repositoryUrl) {
      myLocalFile = localFile;
      myEventType = eventType;
      myRequestType = requestType;
      myResource = resource;
      myException = exception;
      myRepositoryUrl = repositoryUrl;
    }

    public String getRepositoryUrl() {
      return myRepositoryUrl;
    }

    public File getLocalFile() {
      return myLocalFile;
    }

    public EventType getEventType() {
      return myEventType;
    }

    public RequestType getRequestType() {
      return myRequestType;
    }

    public Exception getException() {
      return myException;
    }

    public Resource getResource() {
      return myResource;
    }
  }

  class Resource implements Serializable {
    private final String myResourceName;
    private final long myResourceLastModified;
    private final long myResourceContentLength;

    public Resource(String resourceName, long resourceLastModified, long resourceContentLength) {
      myResourceName = resourceName;
      myResourceLastModified = resourceLastModified;
      myResourceContentLength = resourceContentLength;
    }

    public String getResourceName() {
      return myResourceName;
    }

    public long getResourceLastModified() {
      return myResourceLastModified;
    }

    public long getResourceContentLength() {
      return myResourceContentLength;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Resource resource = (Resource)o;

      if (myResourceContentLength != resource.myResourceContentLength) return false;
      if (myResourceLastModified != resource.myResourceLastModified) return false;
      if (!myResourceName.equals(resource.myResourceName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myResourceName.hashCode();
      result = 31 * result + (int)(myResourceLastModified ^ (myResourceLastModified >>> 32));
      result = 31 * result + (int)(myResourceContentLength ^ (myResourceContentLength >>> 32));
      return result;
    }
  }

}