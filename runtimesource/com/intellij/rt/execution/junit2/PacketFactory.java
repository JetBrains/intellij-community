package com.intellij.rt.execution.junit2;

import com.intellij.rt.execution.junit2.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit2.segments.Packet;
import junit.framework.Test;

public interface PacketFactory {
  Packet createPacket(OutputObjectRegistryImpl registry, Test test);
}
