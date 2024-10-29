// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.security.ssl;

import java.security.cert.CertificateException;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

class Multiplexor {
  private final ConcurrentHashMap<Integer, CompletableFuture<String>> requests = new ConcurrentHashMap<>();
  private final Thread reader = new Thread(this::read, "Maven Server Delegating SSL Reader");
  private final AtomicInteger counter = new AtomicInteger(1);

  private final Scanner myScanner = new Scanner(System.in);

  private void read() {
    while (myScanner.hasNextLine()) {
      readNextResponse();
    }
  }

  private void readNextResponse() {
    String line = myScanner.nextLine();
    if (!SslIDEConfirmingTrustStore.IDE_DELEGATE_TRUST_MANAGER.equals(line)) return;
    line = myScanner.nextLine();
    if (!SslIDEConfirmingTrustStore.DELEGATE_RESPONSE.equals(line)) return;
    line = myScanner.nextLine();
    try {
      Integer key = Integer.parseInt(line);
      line = myScanner.nextLine();
      if (line.equals(SslIDEConfirmingTrustStore.DELEGATE_RESPONSE_ERROR)) {
        CompletableFuture<String> future = requests.get(key);
        if (future != null) future.completeExceptionally(new CertificateException());
      }
      else if (line.equals(SslIDEConfirmingTrustStore.DELEGATE_RESPONSE_OK)) {
        CompletableFuture<String> future = requests.get(key);
        if (future != null) future.complete("ok");
      }
    }
    catch (NumberFormatException ignore) {
    }
  }

  void start() {
    reader.setDaemon(true);
    reader.start();
  }

  void waitForResponse(Integer key) throws CertificateException {
    try {
      requests.get(key).get();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    catch (ExecutionException e) {
      throw new CertificateException(e.getMessage());
    }
  }

  Integer getKey() {
    Integer key = counter.getAndIncrement();
    requests.put(key, new CompletableFuture<>());
    return key;
  }
}
