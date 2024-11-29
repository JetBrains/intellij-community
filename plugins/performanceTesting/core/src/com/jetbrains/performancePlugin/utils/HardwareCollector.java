/*
 * MIT License
 *
 * Copyright (c) 2016-2022 The OSHI Project Contributors: https://github.com/oshi/oshi/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.jetbrains.performancePlugin.utils;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.TroubleInfoCollector;
import org.jetbrains.annotations.NotNull;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.hardware.CentralProcessor.TickType;
import oshi.software.os.*;
import oshi.util.FormatUtil;
import oshi.util.Util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HardwareCollector implements TroubleInfoCollector {
  private final static Logger logger = Logger.getInstance(HardwareCollector.class);

  private final List<String> info = new ArrayList<>();

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    return collectHardwareInfo();
  }

  public String collectHardwareInfo() {
    if (!JnaLoader.isLoaded()) {
      return "Failed to collect computer system info: JNA is not loaded)";
    }

    try {
      SystemInfo si = new SystemInfo();

      HardwareAbstractionLayer hal = si.getHardware();
      OperatingSystem os = si.getOperatingSystem();

      try {
        info.add("=====OS SUMMARY=====\n");
        printOperatingSystem(os);
        printComputerSystem(hal.getComputerSystem());
      }
      catch (Throwable e) {
        logger.warn("Failed to collect computer system info", e);
        info.add("Failed to collect computer system info: " + e.getMessage());
      }

      try {
        info.add("\n=====CPU SUMMARY=====\n");
        CentralProcessor processor = hal.getProcessor();
        printProcessor(processor);
        printCpu(processor);
      }
      catch (Throwable e) {
        logger.warn("Failed to collect processor info", e);
        info.add("Failed to collect processor info: " + e.getMessage());
      }

      try {
        GlobalMemory memory = hal.getMemory();
        info.add("\n=====MEMORY SUMMARY=====\n");
        printMemory(memory);

        info.add("\n=====PROCESSES SUMMARY=====\n");
        printProcesses(os, memory);
      }
      catch (Throwable e) {
        logger.warn("Failed to collect memory info", e);
        info.add("Failed to collect memory info: " + e.getMessage());
      }

      try {
        info.add("\n=====FILESYSTEM SUMMARY=====\n");
        printDisks(hal.getDiskStores());
        printLVgroups(hal.getLogicalVolumeGroups());
        printFileSystem(os.getFileSystem());
      }
      catch (Throwable e) {
        logger.warn("Failed to collect filesystem info", e);
        info.add("Failed to collect filesystem info: " + e.getMessage());
      }

      try {
        info.add("\n=====NETWORK SUMMARY=====\n");
        printNetworkInterfaces(hal.getNetworkIFs());
        printNetworkParameters(os.getNetworkParams());
      }
      catch (Throwable e) {
        logger.warn("Failed to collect network info", e);
        info.add("Failed to collect network info: " + e.getMessage());
      }

      try {
        info.add("\n=====GRAPHICS SUMMARY=====\n");
        printDisplays(hal.getDisplays());
        printGraphicsCards(hal.getGraphicsCards());
      }
      catch (Throwable e) {
        logger.warn("Failed to collect network info", e);
        info.add("Failed to collect network info: " + e.getMessage());
      }
      StringBuilder output = new StringBuilder();
      for (String s : info) {
        output.append(s);
        if (s != null && !s.endsWith("\n")) {
          output.append('\n');
        }
      }
      return output.toString();
    } finally {
      info.clear();
    }
  }

  private void printOperatingSystem(final OperatingSystem os) {
    info.add(String.valueOf(os));
    info.add("Booted: " + Instant.ofEpochSecond(os.getSystemBootTime()));
    info.add("Uptime: " + FormatUtil.formatElapsedSecs(os.getSystemUptime()));
    info.add("Running with" + (os.isElevated() ? "" : "out") + " elevated permissions.");
  }

  private void printComputerSystem(final ComputerSystem computerSystem) {
    info.add("System: " + computerSystem.toString());
  }

  private void printProcessor(CentralProcessor processor) {
    info.add(processor.toString());
    info.add(" Cores:");
    for (CentralProcessor.PhysicalProcessor p : processor.getPhysicalProcessors()) {
      info.add("  " + p.getPhysicalProcessorNumber() + ": efficiency=" + p.getEfficiency() + ", id="
               + p.getIdString());
    }
  }

  private void printMemory(GlobalMemory memory) {
    info.add("Physical Memory: \n " + memory.toString());
    VirtualMemory vm = memory.getVirtualMemory();
    info.add("Virtual Memory: \n " + vm.toString());
    List<PhysicalMemory> pmList = memory.getPhysicalMemory();
    if (!pmList.isEmpty()) {
      info.add("Physical Memory: ");
      for (PhysicalMemory pm : pmList) {
        info.add(" " + pm.toString());
      }
    }
  }

  private void printCpu(CentralProcessor processor) {
    long[] prevTicks = processor.getSystemCpuLoadTicks();
    long[][] prevProcTicks = processor.getProcessorCpuLoadTicks();
    // Wait a second...
    Util.sleep(1000);
    long[] ticks = processor.getSystemCpuLoadTicks();
    long user = ticks[TickType.USER.getIndex()] - prevTicks[TickType.USER.getIndex()];
    long nice = ticks[TickType.NICE.getIndex()] - prevTicks[TickType.NICE.getIndex()];
    long sys = ticks[TickType.SYSTEM.getIndex()] - prevTicks[TickType.SYSTEM.getIndex()];
    long idle = ticks[TickType.IDLE.getIndex()] - prevTicks[TickType.IDLE.getIndex()];
    long iowait = ticks[TickType.IOWAIT.getIndex()] - prevTicks[TickType.IOWAIT.getIndex()];
    long irq = ticks[TickType.IRQ.getIndex()] - prevTicks[TickType.IRQ.getIndex()];
    long softirq = ticks[TickType.SOFTIRQ.getIndex()] - prevTicks[TickType.SOFTIRQ.getIndex()];
    long steal = ticks[TickType.STEAL.getIndex()] - prevTicks[TickType.STEAL.getIndex()];
    long totalCpu = user + nice + sys + idle + iowait + irq + softirq + steal;

    info.add(String.format(
      "User: %.1f%% Nice: %.1f%% System: %.1f%% Idle: %.1f%% IOwait: %.1f%% IRQ: %.1f%% SoftIRQ: %.1f%% Steal: %.1f%%",
      100d * user / totalCpu, 100d * nice / totalCpu, 100d * sys / totalCpu, 100d * idle / totalCpu,
      100d * iowait / totalCpu, 100d * irq / totalCpu, 100d * softirq / totalCpu, 100d * steal / totalCpu));
    info.add(String.format("CPU load: %.1f%%", processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100));
    double[] loadAverage = processor.getSystemLoadAverage(3);
    info.add("CPU load averages:" + (loadAverage[0] < 0 ? " N/A" : String.format(" %.2f", loadAverage[0]))
             + (loadAverage[1] < 0 ? " N/A" : String.format(" %.2f", loadAverage[1]))
             + (loadAverage[2] < 0 ? " N/A" : String.format(" %.2f", loadAverage[2])));
    // per core CPU
    StringBuilder procCpu = new StringBuilder("CPU load per processor:");
    double[] load = processor.getProcessorCpuLoadBetweenTicks(prevProcTicks);
    for (double avg : load) {
      procCpu.append(String.format(" %.1f%%", avg * 100));
    }
    info.add(procCpu.toString());
    long freq = processor.getProcessorIdentifier().getVendorFreq();
    if (freq > 0) {
      info.add("Vendor Frequency: " + FormatUtil.formatHertz(freq));
    }
    freq = processor.getMaxFreq();
    if (freq > 0) {
      info.add("Max Frequency: " + FormatUtil.formatHertz(freq));
    }
    long[] freqs = processor.getCurrentFreq();
    if (freqs[0] > 0) {
      StringBuilder sb = new StringBuilder("Current Frequencies: ");
      for (int i = 0; i < freqs.length; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(FormatUtil.formatHertz(freqs[i]));
      }
      info.add(sb.toString());
    }
  }

  private void printProcesses(OperatingSystem os, GlobalMemory memory) {
    OSProcess myProc = os.getProcess(os.getProcessId());
    if (myProc == null) {
      return;
    }
    info.add(
      "My PID: " + myProc.getProcessID() + " with affinity " + Long.toBinaryString(myProc.getAffinityMask()));
    info.add("Processes: " + os.getProcessCount() + ", Threads: " + os.getThreadCount());
    // Sort by highest CPU
    List<OSProcess> procs = os.getProcesses(OperatingSystem.ProcessFiltering.ALL_PROCESSES, OperatingSystem.ProcessSorting.CPU_DESC, 5);
    info.add("   PID  %CPU %MEM       VSZ       RSS Name");
    for (int i = 0; i < procs.size() && i < 5; i++) {
      OSProcess p = procs.get(i);
      info.add(String.format(" %5d %5.1f %4.1f %9s %9s %s", p.getProcessID(),
                             100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime(),
                             100d * p.getResidentSetSize() / memory.getTotal(), FormatUtil.formatBytes(p.getVirtualSize()),
                             FormatUtil.formatBytes(p.getResidentSetSize()), p.getName()));
    }
    OSProcess p = os.getProcess(os.getProcessId());
    info.add("Current process environment: ");
    for (Map.Entry<String, String> e : p.getEnvironmentVariables().entrySet()) {
      info.add("  " + e.getKey() + "=" + e.getValue());
    }
  }


  private void printDisks(List<HWDiskStore> list) {
    info.add("Disks:");
    for (HWDiskStore disk : list) {
      info.add(" " + disk.toString());

      List<HWPartition> partitions = disk.getPartitions();
      for (HWPartition part : partitions) {
        info.add(" |-- " + part.toString());
      }
    }
  }

  private void printLVgroups(List<LogicalVolumeGroup> list) {
    if (!list.isEmpty()) {
      info.add("Logical Volume Groups:");
      for (LogicalVolumeGroup lvg : list) {
        info.add(" " + lvg.toString());
      }
    }
  }

  private void printFileSystem(FileSystem fileSystem) {
    info.add("File System:");

    info.add(String.format(" File Descriptors: %d/%d", fileSystem.getOpenFileDescriptors(),
                           fileSystem.getMaxFileDescriptors()));

    for (OSFileStore fs : fileSystem.getFileStores()) {
      long usable = fs.getUsableSpace();
      long total = fs.getTotalSpace();
      info.add(String.format(
        " %s (%s) [%s] %s of %s free (%.1f%%), %s of %s files free (%.1f%%) is %s "
        + (fs.getLogicalVolume() != null && fs.getLogicalVolume().length() > 0 ? "[%s]" : "%s")
        + " and is mounted at %s",
        fs.getName(), fs.getDescription().isEmpty() ? "file system" : fs.getDescription(), fs.getType(),
        FormatUtil.formatBytes(usable), FormatUtil.formatBytes(fs.getTotalSpace()), 100d * usable / total,
        FormatUtil.formatValue(fs.getFreeInodes(), ""), FormatUtil.formatValue(fs.getTotalInodes(), ""),
        100d * fs.getFreeInodes() / fs.getTotalInodes(), fs.getVolume(), fs.getLogicalVolume(),
        fs.getMount()));
    }
  }

  private void printNetworkInterfaces(List<NetworkIF> list) {
    StringBuilder sb = new StringBuilder("Network Interfaces:");
    if (list.isEmpty()) {
      sb.append(" Unknown");
    }
    else {
      for (NetworkIF net : list) {
        if (net.getIfAlias() != null) {
          sb.append("\n ").append(net);
        }
      }
    }
    info.add(sb.toString());
  }

  private void printNetworkParameters(NetworkParams networkParams) {
    info.add("Network parameters:\n " + networkParams.toString());
  }

  private void printDisplays(List<Display> list) {
    info.add("Displays:");
    int i = 0;
    for (Display display : list) {
      info.add(" Display " + i + ":");
      info.add(String.valueOf(display));
      i++;
    }
  }

  private void printGraphicsCards(List<GraphicsCard> list) {
    info.add("Graphics Cards:");
    if (list.isEmpty()) {
      info.add(" None detected.");
    }
    else {
      for (GraphicsCard card : list) {
        info.add(" " + card);
      }
    }
  }

  @Override
  public String toString() {
    return "Hardware";
  }
}
