package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit.segments.Packet;
import junit.framework.Test;

public interface PacketFactory {
  Packet createPacket(OutputObjectRegistryImpl registry, Test test);
}
