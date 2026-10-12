---
topic: testing
---

# 182. The daemon is reached through the exec channel

Date: 2026-09-29

## Status

Accepted. It amends [ADR 0106](0106-a-warm-daemon-survives-a-lease-release.md): the host-side network checks of
a `no_connection` refusal no longer apply. It adds the `relay` verb to the `avl-guest` row of
[ADR 0059](0059-the-ui-lane-tooling-is-rust.md). How the lane uses the relay is
[the VM guide](../vm-ui-tests.md).

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): `avl-daemon` and `avl-worker` are the
`daemon` and `worker` modules of `avl-vm`. So the connector is `crates/avl-vm/src/daemon/http/connector.rs`, and each
test file below moved with its module. The relay and the measurements stand. The measurement of `tart exec -i` is the
evidence of the raw pull on Tart.

## Context

The host controller `vm` reached the guest daemon over TCP. It asked the hypervisor for the guest address, a
`192.168.64.*` address on the shared vmnet bridge, and connected to the daemon port there.

macOS 15 and later has Local Network privacy. It denies a connection to a local network address to every app that
Apple does not ship, until a person grants that app. The controller runs under the IDE or a terminal, so it
inherits the grant of that app. A live walk on 2026-09-27 found every probe failing with "No route to host" while
`tart exec` worked ([ADR 0106](0106-a-warm-daemon-survives-a-lease-release.md)).

On 2026-09-28 a check for that condition was added. `crates/avl-daemon/src/reach.rs` compared the connect of the
controller with `/usr/bin/nc`, and refused `local_network_denied` at exit 77. The documents told a person to grant
the app under System Settings.

Tart's FAQ documents the Local Network pop-up. It offers two remedies only:

- a click on Allow, which a person must do for each app on each Mac;
- `sudo defaults write com.apple.network.local-network AllowedEthernetLocalNetworkAddresses …`, then a reboot.

Neither is acceptable for every QA machine.

The controller already does everything else without the guest network:

- `tart exec` and `prlctl exec` run every guest verb. `tart exec` uses the `control.sock` of the VM and vsock.
- The virtio-fs shares carry the host-built outputs into the guest.
- The trace pull does not use the guest network either.

Only the daemon connection used the guest network. The exec channel was measured on 2026-09-29, on macOS 26.7
with Tart 2.38.0 and the worker `air-linux-1`:

| probe | result |
|---|---|
| `tart exec air-linux-1 true` | 0.65 s |
| 300 000 bytes through `tart exec -i air-linux-1 cat` | the bytes come back md5-identical |
| `printf '' \| tart exec -i air-linux-1 nc 127.0.0.1 22` | the sshd banner arrives at once |

So the exec channel carries a byte stream to a guest loopback port, and it needs no network permission.

## Decision

**The controller reaches the guest daemon through the exec channel, never through the guest network.**

1. **The guest agent has a `relay <port>` verb.** The relay bridges its stdin and stdout to `127.0.0.1:<port>`
   inside the guest. It ends when the daemon side of the connection ends. A refused connect writes nothing on
   stdout, so the controller never reads a partial reply.
2. **The controller speaks HTTP/1.1 over the pipes of a relay child.** For each pooled HTTP connection, a hyper
   connector in `crates/avl-daemon/src/http/connector.rs` spawns one relay:

   ```text
   tart exec -i <worker> <agent> relay <port>
   prlctl exec <worker> "'<agent>' 'relay' '<port>'"
   ```

   The first line is Tart. The second line is Parallels.
3. **The guest daemon binds `127.0.0.1`.** `AirUiDaemonServer` no longer binds `0.0.0.0`. The token stays on every
   request, as a defence against other processes in the guest.
4. **The daemon record names the worker, not the guest address.** `HostState` loses `guest_ip` and gains `worker`.
5. **The network diagnosis is deleted.** These go:
   - `reach.rs`, exit 77 and the refusal `local_network_denied`;
   - the guest address lookup and its refusal `daemon_no_guest_ip`;
   - the `AIR_VM_NETWORK`, `route -n get` and VPN advice of a `no_connection` refusal;
   - every sentence about Local Network privacy in the guide, the skill and the spec.
6. **A relay that never opened names the relay.** The `no_connection` message of the health poll is:

   ```text
   the daemon published its state file, but no relay to 127.0.0.1:<port> inside <worker> opened in <seconds> s (<probes> probes; last: <detail>); read `daemon log`
   ```

These tests pin the decision:

- `crates/avl-guest/src/relay/tests.rs`, the relay verb;
- `crates/avl-host-sys/src/proc/tests.rs`, the byte stream of an exec channel child;
- `crates/avl-daemon/src/http/connector/tests.rs`, the connector;
- `crates/avl-worker/src/worker/tests.rs`, the relay argv of both backends;
- `crates/avl-daemon/src/http/tests.rs` and `crates/avl-daemon/src/start/tests.rs`, the health poll and its
  refusal.

## Consequences

- **No Mac needs a prompt or a grant.** The controller works under any app, on any macOS version, with no
  System Settings step and no reboot.
- **Each pooled HTTP connection costs one relay spawn.** A spawn takes about 0.65 s. A reused connection pays
  nothing more.
- **The host network cannot reach the guest daemon.** No host process and no LAN peer can connect to the daemon
  port. Only a process in the guest can, and the token refuses it.
- **The network mode no longer affects the daemon.** `AIR_VM_NETWORK` stays, because it still sets the network of
  the worker for the internet access of the guest. `nat`, `softnet` and `bridged` give the daemon the same path.
- **The Parallels line is verified by argv tests only.** No live Parallels run proved the relay on 2026-09-29.
- **ADR 0106's host-side advice is void.** Its sentence about the host-side checks of `no_connection` points here.

## Alternatives rejected

- **The System Settings grant.** A person must grant each app on each Mac. The grant follows the app that runs
  the controller, not the controller itself.
- **Tart's `defaults write` allow-list.** It needs `sudo` and a reboot on every host. A QA machine cannot take that
  step for a test lane.
- **An `ssh -L` tunnel through `/usr/bin/ssh`.** It works only because Apple exempts its own platform binaries from
  the check. It also needs a key provisioned into each guest and trusted on each host.
- **A local TCP listener per worker.** The controller would forward a host loopback port into the relay. That
  adds a port and a lifetime to manage, and the HTTP client gains nothing from it.
- **A Docker backend.** Docker avoids the check only because it publishes ports on `127.0.0.1`. The relay gives
  Tart the same loopback path without a third backend.
