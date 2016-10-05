package com.jetbrains.python.debugger.pydev.transport;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.pydev.ProtocolFrame;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Alexander Koshevoy
 */
public class ClientModeDebuggerTransport extends BaseDebuggerTransport {
  private static final Logger LOG = Logger.getInstance(ClientModeDebuggerTransport.class);

  private static final ScheduledExecutorService myScheduledExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
    private AtomicInteger num = new AtomicInteger(1);

    @Override
    public Thread newThread(@NotNull Runnable r) {
      return new Thread(r, "Python Debug Script Connection " + num.getAndIncrement());
    }
  });

  private static final int MAX_CONNECTION_TRIES = 10;
  private static final long CHECK_CONNECTION_APPROVED_DELAY = 1000L;
  private static final long SLEEP_TIME_BETWEEN_CONNECTION_TRIES = 150L;

  @NotNull private final IPyDebugProcess myDebugProcess;

  @NotNull private final String myHost;
  private final int myPort;

  @NotNull private State myState = State.INIT;

  @Nullable private Socket mySocket;
  @Nullable private DebuggerReader myDebuggerReader;

  public ClientModeDebuggerTransport(@NotNull IPyDebugProcess debugProcess,
                                     @NotNull RemoteDebugger debugger,
                                     @NotNull String host,
                                     int port) {
    super(debugger);
    myDebugProcess = debugProcess;
    myHost = host;
    myPort = port;
  }

  @Override
  public void waitForConnect() throws IOException {
    try {
      Thread.sleep(500L);
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
    synchronized (mySocketObject) {
      if (myState != State.INIT) {
        throw new IllegalStateException(
          "Inappropriate state of Python debugger for connecting to Python debugger: " + myState + "; " + State.INIT + " is expected");
      }

      doConnect();
    }
  }

  private void doConnect() throws IOException {
    synchronized (mySocketObject) {
      if (mySocket != null) {
        try {
          mySocket.close();
        }
        catch (IOException e) {
          LOG.debug("Failed to close previously opened socket", e);
        }
        finally {
          mySocket = null;
        }
      }

      int i = 0;
      boolean connected = false;
      while (!connected && i < MAX_CONNECTION_TRIES) {
        i++;
        try {
          Socket clientSocket = new Socket();
          clientSocket.setSoTimeout(0);
          clientSocket.connect(new InetSocketAddress(myHost, myPort));

          try {
            myDebuggerReader = new DebuggerReader(myDebugger, clientSocket.getInputStream());
          }
          catch (IOException e) {
            LOG.debug("Failed to create debugger reader", e);
            throw e;
          }

          mySocket = clientSocket;
          connected = true;
        }
        catch (ConnectException e) {
          if (i < MAX_CONNECTION_TRIES) {
            try {
              Thread.sleep(SLEEP_TIME_BETWEEN_CONNECTION_TRIES);
            }
            catch (InterruptedException e1) {
              throw new IOException(e1);
            }
          }
        }
      }

      if (!connected) {
        myState = State.DISCONNECTED;
        throw new IOException("Failed to connect to debugging script");
      }

      myState = State.CONNECTED;
      LOG.debug("Connected to Python debugger script on #" + i + " attempt");


      try {
        myDebugProcess.init();
        myDebugger.run();
      }
      catch (PyDebuggerException e) {
        myState = State.DISCONNECTED;
        throw new IOException("Failed to send run command", e);
      }

      myScheduledExecutor.schedule(() -> {
        synchronized (mySocketObject) {
          if (myState == State.CONNECTED) {
            try {
              LOG.debug("Reconnecting...");
              doConnect();
            }
            catch (IOException e) {
              LOG.debug(e);
              myDebugger.fireCommunicationError();
            }
          }
        }
      }, CHECK_CONNECTION_APPROVED_DELAY, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  protected boolean sendMessageImpl(byte[] packed) throws IOException {
    synchronized (mySocketObject) {
      if (mySocket == null) {
        return false;
      }
      final OutputStream os = mySocket.getOutputStream();
      os.write(packed);
      os.flush();
      return true;
    }
  }

  @Override
  public void close() {
    synchronized (mySocketObject) {
      try {
        if (myDebuggerReader != null) {
          myDebuggerReader.stop();
        }
      }
      finally {
        if (mySocket != null) {
          try {
            mySocket.close();
          }
          catch (IOException ignored) {
          }
        }
      }
    }
  }

  @Override
  public boolean isConnected() {
    synchronized (mySocketObject) {
      return myState == State.APPROVED;
      //return myConnected && mySocket != null && !mySocket.isClosed();
    }
  }

  @Override
  public void disconnect() {
    // TODO disconnect?
  }

  @Override
  public void messageReceived(@NotNull ProtocolFrame frame) {
    synchronized (mySocketObject) {
      if (myState == State.CONNECTED) {
        myState = State.APPROVED;
      }
    }
  }

  private enum State {
    /**
     * Before calling {@link #waitForConnect()}
     */
    INIT,
    /**
     * Connection to the debugger script established
     */
    CONNECTED,
    /**
     * Established connection to the debugger script has been approved
     */
    APPROVED,
    /**
     * Debugger disconnected
     */
    DISCONNECTED
  }

  public class DebuggerReader extends BaseDebuggerReader {
    public DebuggerReader(@NotNull RemoteDebugger debugger, @NotNull InputStream stream) throws IOException {
      super(stream, CharsetToolkit.UTF8_CHARSET, debugger); //TODO: correct encoding?
      start(getClass().getName());
    }

    protected void onCommunicationError() {
      synchronized (mySocketObject) {
        if (myState == State.APPROVED) {
          getDebugger().fireCommunicationError();
        }
      }
    }
  }
}
