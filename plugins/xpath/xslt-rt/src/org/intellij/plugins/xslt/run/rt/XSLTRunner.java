/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.xslt.run.rt;

import org.xml.sax.SAXParseException;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/** @noinspection CallToPrintStackTrace,UseOfSystemOutOrSystemErr,IOResourceOpenedButNotSafelyClosed,SocketOpenedButNotSafelyClosed,UseOfArchaicSystemPropertyAccessors */
public class XSLTRunner implements XSLTMain {

    private XSLTRunner() {
    }

    public static void main(String[] args) throws Throwable {
        final XSLTMain main = loadMain();

        TransformerFactory transformerFactory;
        try {
            transformerFactory = main.createTransformerFactory();
        } catch (AbstractMethodError e) {
            // old debugger
            transformerFactory = createTransformerFactoryStatic();
        } catch (ClassNotFoundException e) {
            transformerFactory = createTransformerFactoryStatic();
        }

        final String uriResolverClass = System.getProperty("xslt.uri-resolver");
        if (uriResolverClass != null) {
            transformerFactory.setURIResolver((URIResolver)Class.forName(uriResolverClass).newInstance());
        }

        final boolean[] trouble = new boolean[]{ false };
        final MyErrorListener listener = new MyErrorListener(trouble);
        final boolean isSmartErrorHandling = System.getProperty("xslt.smart-error-handling", "false").equals("true");
        if (isSmartErrorHandling) {
            transformerFactory.setErrorListener(listener);
        }

        final File xslt = new File(System.getProperty("xslt.file"));
        try {
            final Transformer transformer = transformerFactory.newTransformer(new StreamSource(xslt));
            if (transformer != null && !trouble[0]) {
                final Enumeration props = System.getProperties().keys();
                while (props.hasMoreElements()) {
                    String s = (String)props.nextElement();
                    if (s.startsWith("xslt.param.")) {
                        final String name = s.substring("xslt.param.".length());
                        final String value = System.getProperty(s);
                        transformer.setParameter(name, value);
                    }
                }

                final File input = new File(System.getProperty("xslt.input"));
                final String out = System.getProperty("xslt.output");
                OutputStream fileStream;
                if (out != null) {
                  final File output = new File(out);
                  fileStream = new BufferedOutputStream(new FileOutputStream(output));
                } else {
                  fileStream = null;
                }
                final StreamResult result;

                final Integer _port = Integer.getInteger("xslt.listen-port", -1);
                final int port = _port.intValue();
                if (port != -1) {
                    // block until IDEA connects
                    try {
                        final ServerSocket serverSocket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
                        serverSocket.setSoTimeout(Integer.getInteger("xslt.listen-timeout", 5000).intValue());
                        final Socket socket = serverSocket.accept();
                        final BufferedOutputStream socketStream = new BufferedOutputStream(socket.getOutputStream(), 16);

                        if (out != null) {
                            result = new StreamResult(new ForkedOutputStream(new OutputStream[]{ socketStream, fileStream }));
                        } else {
                            result = new StreamResult(new OutputStreamWriter(socketStream, "UTF-8"));
                        }
                    } catch (SocketTimeoutException ignored) {
                        System.err.println("Plugin did not connect to runner within timeout. Run aborted.");
                        return;
                    }
                } else {
                    final String encoding = System.getProperty("file.encoding");
                    if (encoding != null) {
                        // ensure proper encoding in xml declaration
                        transformer.setOutputProperty("encoding", encoding);
                        if (out != null) {
                          result = new StreamResult(new OutputStreamWriter(new ForkedOutputStream(new OutputStream[]{System.out, fileStream}), encoding));
                        } else {
                          result = new StreamResult(new OutputStreamWriter(System.out, encoding));
                        }
                      } else {
                        if (out != null) {
                          result = new StreamResult(new ForkedOutputStream(new OutputStream[]{ System.out, fileStream }));
                        } else {
                          result = new StreamResult(System.out);
                        }
                      }
                }

                Runtime.getRuntime().addShutdownHook(new Thread("XSLT runner") {
                  public void run() {
                    try {
                      final Writer out = result.getWriter();
                      if (out != null) {
                        out.flush();
                        out.close();
                      } else if (result.getOutputStream() != null) {
                        result.getOutputStream().flush();
                        result.getOutputStream().close();
                      }
                    } catch (IOException e) {
                      // no chance to fix...
                    }
                  }
                });

                main.start(transformer, new StreamSource(input), result);
            }
        } catch (TransformerException e) {
            if (isSmartErrorHandling) {
                listener.error(e);
            } else {
                throw e;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public TransformerFactory createTransformerFactory() throws Exception {
        return createTransformerFactoryStatic();
    }

    public static TransformerFactory createTransformerFactoryStatic() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        final String factoryClass = System.getProperty("xslt.transformer-factory");
        if (factoryClass != null) {
            return (TransformerFactory)Class.forName(factoryClass).newInstance();
        } else {
            return TransformerFactory.newInstance();
        }
    }

    public void start(Transformer transformer, Source source, Result result) throws TransformerException {
        transformer.transform(source, result);
    }

    private static XSLTMain loadMain() {
        final String mainClass = System.getProperty("xslt.main");
        if (mainClass == null) {
            return new XSLTRunner();
        }

        try {
            return (XSLTMain)Class.forName(mainClass).newInstance();
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InstantiationException e) {
            throw new AssertionError(e);
        }
    }

    /** @noinspection UseOfSystemOutOrSystemErr*/
    private static class MyErrorListener implements ErrorListener {
        private final Set myMessages = new HashSet();
        private final boolean[] myTrouble;

        public MyErrorListener(boolean[] trouble) {
            myTrouble = trouble;
        }

        public void warning(TransformerException exception) {
            handleException(exception, "WARNING");
        }

        public void error(TransformerException exception) {
            handleException(exception, "ERROR");
            myTrouble[0] = true;
        }

        public void fatalError(TransformerException exception) {
            handleException(exception, "FATAL");
            myTrouble[0] = true;
        }

        private void handleException(TransformerException exception, String type) {
            final String message = getMessage(exception);
            if (!myMessages.contains(message)) {
                System.err.println("[" + type + "]: " + message);
                myMessages.add(message);
            }
        }

        private static String getMessage(TransformerException exception) {
            final SourceLocator[] locators = new SourceLocator[]{ exception.getLocator() };
            final String[] messages = new String[1];
            findLocator(exception, locators, messages);

            final SourceLocator locator = locators[0];
            if (locator != null) {
                final String systemId = locator.getSystemId();
                if (systemId != null) {
                    String s = systemId.replaceAll(" ", "%20") + ": ";
                    final int lineNumber = locator.getLineNumber();
                    if (lineNumber != -1) {
                        s += "line " + lineNumber + ": ";
                        final int columnNumber = locator.getColumnNumber();
                        if (columnNumber != -1) {
                            s += "column " + columnNumber + ": ";
                        }
                    }
                    return s + (messages[0] != null ? messages[0] : exception.getMessage());
                }
            }
            return messages[0] != null ? messages[0] : exception.getMessage();
        }

        private static void findLocator(Throwable exception, SourceLocator[] locators, String[] messages) {
            if (exception instanceof TransformerException) {
                final TransformerException t = (TransformerException)exception;

                if (t.getLocator() != null) {
                    messages[0] = t.getMessage();
                    locators[0] = t.getLocator();
                } else if (exception.getCause() != null) {
                    findLocator(exception.getCause(), locators, messages);
                }
            } else if (exception instanceof SAXParseException) {
                final SAXParseException sae = (SAXParseException)exception;

                messages[0] = sae.getMessage();
                locators[0] = new SourceLocator() {
                    public int getColumnNumber() {
                        return sae.getColumnNumber();
                    }

                    public int getLineNumber() {
                        return sae.getLineNumber();
                    }

                    public String getPublicId() {
                        //noinspection ConstantConditions
                        return null;
                    }

                    public String getSystemId() {
                        return sae.getSystemId();
                    }
                };
            } else if (exception.getCause() != null) {
                findLocator(exception.getCause(), locators, messages);
            }

            try {
                final Throwable t = (Throwable)exception.getClass().getMethod("getException", new Class[0]).invoke(exception, new Object[0]);
                if (t != exception) {
                    findLocator(t, locators, messages);
                }
            } catch (Exception e) {
                //
            }
        }
    }

    static class ForkedOutputStream extends OutputStream {
        OutputStream[] outs;

        ForkedOutputStream(OutputStream[] out) {
            outs = out;
        }

        public void write(byte[] b, int off, int len) throws IOException {
          for (int i = 0, outsLength = outs.length; i < outsLength; i++) {
            outs[i].write(b, off, len);
          }
        }

        public void write(int b) throws IOException {
          for (int i = 0, outsLength = outs.length; i < outsLength; i++) {
            outs[i].write(b);
          }
        }

        public void flush() throws IOException {
          for (int i = 0, outsLength = outs.length; i < outsLength; i++) {
            outs[i].flush();
          }
        }

        public void close() throws IOException {
          for (int i = 0, outsLength = outs.length; i < outsLength; i++) {
            outs[i].close();
          }
        }
    }
}
