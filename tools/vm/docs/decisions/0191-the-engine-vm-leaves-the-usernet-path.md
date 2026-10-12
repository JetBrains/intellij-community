---
topic: testing
---

# 191. The SSH connection of the engine VM leaves the usernet path

Date: 2026-10-01

## Status

Accepted. It amends [ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md) for the cloud image and
the SSH transport of the Lima engine. How the lane uses the engine is [the VM guide](../vm-ui-tests.md).

## Context

The controller reaches `dockerd` in the Lima engine through the Docker socket that Lima forwards to the host. Lima
forwards a guest Unix socket with an SSH `-L` forward. So every `docker` command, and every byte of an exec channel,
goes through one SSH connection.

Lima 2 on `vz` serves that SSH connection over vsock by default, when the guest has systemd 256 or later. Ubuntu
24.04 has systemd 255. The host-agent log of the engine that ADR 0189 made says so:

```
"msg":"Failed to start vsock forwarder (systemd is older than v256?)"
"msg":"SSH server does not seem to be running on vsock port, using usernet forwarder"
```

So the SSH connection crossed the userspace network stack of the host agent. Over vsock, the host agent forwards a
loopback port straight to the guest's vsock port 22. Ubuntu 26.04 has systemd 259.

A review of the engine proposed three changes. The first is Ubuntu 26.04 for SSH over vsock. The second is the `vzNAT`
network for image pulls. The third is the ASIF format for the disk on a macOS 26 host. A spike measured each change
before any code. The spike host, on 2026-10-01:

| what | value |
|---|---|
| host | macOS 26.7, Apple silicon, 18 cores, 128 GiB |
| host load | a load average of 20 to 55, from other sessions on the same host |
| Lima | 2.2.0, the Bazel pin |
| engine VM | 8 CPUs, 10 GiB, 80 GiB, the template of ADR 0189 |
| Ubuntu 24.04 | kernel 6.8.0-134, systemd 255.4, `docker.io` 29.1.3 |
| Ubuntu 26.04 | kernel 7.0.0-28, systemd 259.5, `docker.io` 29.1.3 |

Five instances ran one at a time: the base template, then the base with each change, then all three changes. The
probes ran three times on each instance. The table shows the four instances that isolate one change. The medians, in
seconds unless named:

| probe | base 24.04 | `vzNAT` | ASIF | 26.04 |
|---|---|---|---|---|
| the SSH transport in the host-agent log | usernet | usernet | usernet | vsock |
| `docker version`, the median of 30 calls, per round, ms | 24, 51, 50 | 54, 50, 43 | 38, 32, 29 | 19, 19, 19 |
| `docker exec` of 1 GiB to the host | 7.26 | 9.55 | 22.90 | 4.69 |
| 1 GiB from the host into `docker exec -i` | 3.72 | 4.02 | 8.19 | 3.75 |
| the pull of the worker image, about 1 GB | 28.0 | 28.4 | 29.4 | 30.2 |
| 2 GiB written with `O_DIRECT` and `fsync` | 0.84 | 1.25 | 2.44 | 1.44 |
| 2 GiB read with `O_DIRECT` | 0.25 | 0.30 | 1.29 | 1.16 |
| a warm `limactl start` | 11.5 | 28.2 | 15.7 | 7.2 |

The host load changed a lot between instances. So a paired check followed: three instances ran side by side, and
each of five rounds visited all three in turn. The medians of the five rounds, at a load average of about 50:

| probe | base 24.04 | ASIF | 26.04 |
|---|---|---|---|
| 2 GiB written with `O_DIRECT` and `fsync` | 2.16 | 3.21 | 2.18 |
| 2 GiB read with `O_DIRECT` | 1.04 | 1.36 | 1.46 |
| `docker version`, the median of 30 calls, ms | 34 | no value | 53 |
| `docker exec` of 1 GiB to the host | 30.9 | no value | 19.5 |
| 1 GiB from the host into `docker exec -i` | 28.5 | no value | 19.8 |

The ASIF instance of the paired check was still provisioning during its rounds, so it has no Docker values. Its disk
rounds ran beside `apt-get`.

Other spike results:

- **The mirror limits the network probe.** A 300 MiB range of the mirrored cloud image ran at about 7 MB/s without
  `vzNAT`, and at 4.3 to 7.3 MB/s with it.
- **The instance with all three changes showed no latency gain.** It used vsock. Its `docker version` medians were 55,
  54 and 54 ms, and its warm starts took 11.3 s to 20.6 s.
- **The dead node of ADR 0189 is shorter on the 26.04 kernel.** The host replaced a file by rename, and the guest
  read it every 5 ms. In three rounds, 17 to 21 reads failed after the rename. The first fresh read came 0.51 s to
  0.56 s after the rename. On 24.04, ADR 0189 measured 0.73 s to 1.02 s.
- **A slow boot hit only Ubuntu 24.04.** Two of five 24.04 creates logged `pam_systemd ... Failed to create session:
  Connection timed out` every 2 minutes, and each hit the 900 s first-start budget. None of three 26.04 creates did.
  The sample is small, and the cause is not known.
- **The ASIF image is small only on paper.** The file had an apparent size of 2.8 GiB against 80 GiB, but the
  allocated size was 2.7 GiB for the ASIF file and 2.5 GiB for the raw file.
- **Ubuntu 26.04 makes the Rust coreutils the default.** Its `dd` refuses `iflag=direct`, and `gnudd` is the GNU
  one. The provision and the probe of the template passed on 26.04 unchanged.

The tables give medians. The commit message of this change holds the raw values of the probes in these tables.

## Decision

**The Lima engine boots Ubuntu 26.04, so its SSH connection runs over vsock. The Docker socket forward uses that
connection.**

1. **The cloud image is Ubuntu 26.04.** The pins are the `release-20260720` images of Lima's own
   `templates/_images/ubuntu-26.04.yaml`. The Space file mirror holds a byte-identical copy under
   `ubuntu-cloud-images/resolute-20260720/`, for the reason of ADR 0189. The names of the four
   `LIMA_BASE_IMAGE_*` pins stay.
2. **The template asks for SSH over vsock.** `ssh.overVsock: true` is Lima's default for `vz`, and the key states
   the intent. Lima falls back to the usernet forwarder only when the guest cannot serve SSH over vsock.
3. **The template sets `param.internal_netplanOptional: "true"`.** Lima's own image template for 26.04 sets it. The
   first-boot rename of the network interface fails on 26.04 (LP: #2136392), and without the parameter
   `systemd-networkd-wait-online` blocks the boot for 120 s.
4. **The Lima floor is 2.0.2.** It is the first release with `ssh.overVsock`. The template's `minimumLimaVersion`
   and `MINIMUM_LIMA_VERSION` in `crates/avl-worker/src/lima.rs` move together, and the pin test keeps them equal.
5. **The 2 s settle stays.** The dead node on the 26.04 kernel expires in less than one second.
6. **The template sets no `vzNAT` network and no ASIF disk.** The primary probe of `vzNAT` is the network, and
   it showed no gain. The primary probe of ASIF is the disk, and it measured worse. So the gate of the spike drops
   both. The alternatives below say why.

## Consequences

- **Every existing engine is made again once.** The image pins move the template digest. So the next start of an
  engine made before this change deletes it and makes it again, with every container and volume. The first start
  downloads the 26.04 image, about 0.9 GB, from the mirror.
- **The lane runs on the new engine, but the lane time on it is not measured yet.** The recreate succeeded. The
  host-agent log of the new engine shows the vsock forwarder and no usernet fallback. The composer class
  `AgentSessionToolWindowComposerUiTest` ran three times on the old engine. Then another session edited the code
  under test and left the edits uncommitted. On the new engine, five attempts failed in the host build on a compile
  error in those files. The table gives the three before runs and the three attempts that built:

  | run | engine | daemon | the eight scenarios of the before runs | verdict of the class |
  |---|---|---|---|---|
  | before 1 | 24.04, usernet | cold | 228.0 s, 8 passed | 8 of 8 |
  | before 2 | 24.04, usernet | warm | 164.8 s, 8 passed | 8 of 8 |
  | before 3 | 24.04, usernet | warm | 240.8 s, 8 passed | 8 of 8 |
  | after 1 | 26.04, vsock | cold | 321.0 s, 7 passed | 7 of 8 |
  | after 2 | 26.04, vsock | warm | 208.3 s, 8 passed | 9 of 10 |
  | after 3 | 26.04, vsock | warm | 206.5 s, 8 passed | 10 of 10 |

  The after runs ran the other session's half-finished code, and two of them also ran two scenarios that the
  other session had added. So they are not a lane measurement of the engine, and this ADR draws no lane-level
  number from them or from the spike. In after 1, `PerformTargetInput` did not answer in "two concurrent launches
  keep independent window bindings". The other session was changing that code. In after 2, the active frame did not
  appear in a scenario that session had just added. Three runs on a tree without uncommitted edits under
  `plugins/air` are still owed. Run this command three times:

  ```bash
  ./plugins/air/scripts/vm.cmd --stream run AgentSessionToolWindowComposerUiTest
  ```
- **The warm start is shorter.** In the spike, three warm starts of 26.04 took 6.6 s to 7.5 s, and three of 24.04
  took 10.4 s to 15.6 s. In the Lima log, a warm start took 7 s inside `pool start`, against 15 s inside a `run` on
  the old engine, one start each. The first start of the new engine took 197 s to `READY`. The image download from
  the mirror and the conversion took 137 s of that. ADR 0189 measured 147.4 s for a first start of 24.04 with a
  download from Ubuntu, so the first starts are not comparable.
- **The per-call latency gain is not shown.** `docker version` was faster on 26.04 alone and slower in the paired
  check. The throughput out of the container was better in both checks. The throughput into the container was equal
  in the single run and better in the paired check. So this ADR claims the transport and the throughput out of the
  container, not the latency.
- **The image pull was not faster on 26.04.** It took a median of 30.2 s against 28.0 s. A pull runs in the guest
  and does not cross the SSH connection.
- **The direct I/O was slower on 26.04.** The read was slower in both checks. The write took 1.44 s against 0.84 s
  in the single run, and it was equal in the paired check. The disk is a secondary probe of 26.04. The lane does not
  use direct I/O.
- **The engine VM runs Ubuntu 26.04 and kernel 7.0.** The `docker.io` of the 26.04 archive was 29.1.3 on the spike
  day, the same version as on 24.04. ADR 0189 still applies: the package is the one the archive has at the first
  start.
- **A guest script in the engine VM gets the Rust coreutils.** A script that needs the GNU behavior calls the command
  with the `gnu` prefix, for example `gnudd`.
- **The escape stays the same.** `DOCKER_HOST` or `DOCKER_BIN` names another engine, as in ADR 0189.

## Alternatives rejected

- **The `vzNAT` network.** It gives the guest Apple's NAT as its default route, at metric 100 under the usernet
  route at 200. The worker image pull took a median of 28.4 s with it and 28.0 s without it. The mirror download ran
  at about 7 MB/s without it, and at 4.3 to 7.3 MB/s with it. So it brings no measured gain. It also adds a second
  route, which a VPN on the host can break, as on the Tart Linux worker with `nat`.
- **The ASIF disk image on a macOS 26 host.** The direct-I/O write and read were slower than raw in the single run
  and in the paired check. The paired ASIF rounds ran beside `apt-get`, because that instance still provisioned.
  Its only gain is the apparent size of the file, and the allocated size is about the same. The `default.yaml` of
  Lima 2.2.0 says that an ASIF disk cannot convert back to raw.
- **A TCP `DOCKER_HOST` through Lima's gRPC vsock forwarder.** It removes SSH from the socket path, but it exposes the
  engine on a loopback TCP port without TLS. No measurement shows SSH over vsock as the bottleneck.
- **Rosetta in the engine VM, and a persistent Docker data disk.** The user declined both on 2026-10-01. A publish
  stays on an engine with emulation, as ADR 0189 says.
- **`drop_caches` after a refresh.** ADR 0189 rejected it, and the shorter dead node gives no new reason.
