package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;

public interface PacketFactory {
  Packet createPacket(OutputObjectRegistryEx registry, Object test);
}
