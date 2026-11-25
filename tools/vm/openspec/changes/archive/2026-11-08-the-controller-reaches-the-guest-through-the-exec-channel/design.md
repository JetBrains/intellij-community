# The controller reaches the guest daemon and the image-build SSH through the exec channel

## Context

macOS has Local Network privacy. It refuses a connection to a local network address from every app that Apple does
not ship, until a person grants that app. The controller runs under an IDE, a terminal or an agent session, so it
inherits the grant of that app. Without the grant, every connect to a guest address fails at once, while `tart exec`
works. The remedies that Tart documents need a person on each Mac, or `sudo` and a reboot on each host.

The controller does all other guest work without the guest network. `tart exec` and `prlctl exec` run every guest
verb over the control socket of the VM. The virtio-fs shares carry the host outputs. The exec channel also carries a
byte stream to a guest loopback port with no network permission.

Two connections need a guest port: the connection to the lane daemon, and the Packer SSH of `image build`.
Packer takes no custom transport. Its SSH communicator offers a SOCKS5 proxy and an SSH bastion. The Tart plugin
overwrites `ssh_host` with the address from `tart ip`.

The contract is in [supervision and evidence](../../../../spec/lane-controller.spec.md#supervision-and-evidence).

## Decisions

**The controller reaches the guest through the exec channel, never through the guest network.**

### The lane daemon

1. **The guest agent has a `relay <port>` verb.** It bridges its standard input and output to `127.0.0.1:<port>` in
   the guest, and it ends when the daemon side ends. A refused connect writes nothing, so the controller never reads
   a partial reply. The verb is in [relay.rs](../../../../crates/avl-guest/src/relay.rs).
2. **The controller speaks HTTP/1.1 over the pipes of one relay child per pooled connection.** The connector in
   [connector.rs](../../../../crates/avl-vm/src/daemon/http/connector.rs) spawns
   `tart exec -i <worker> <agent> relay <port>` on Tart and the `prlctl exec` form on Parallels.
3. **The lane daemon binds `127.0.0.1`.** The token stays on every request, as a guard against other processes in
   the guest.
4. **The daemon record names the worker, not a guest address.** `HostState` has `worker` and no guest IP.
5. **No network diagnosis exists.** No refusal of the controller asks about Local Network privacy, routes or VPNs.
6. **A relay that never opened names the relay.** The `no_connection` refusal of the health poll names the worker,
   the loopback port and `daemon log`.

The container-linux backend is the one exception. There the controller reaches the daemon on a published host
loopback port. See [the lane runs in the testing-ui container](../2026-10-04-the-lane-runs-in-the-testing-ui-container/design.md).

### The image build

1. **`image build` serves Packer a SOCKS5 proxy on a loopback port.** Each connection of the proxy is one
   `tart exec -i <candidate> /usr/bin/nc 127.0.0.1 22`. The proxy takes only `CONNECT` with no authentication, and
   only to port 22. It ignores the address that Packer asks for, because it serves the one candidate of the build.
   The proxy is in [ssh_relay.rs](../../../../crates/avl-vm/src/image/ssh_relay.rs).
2. **The proxy answers success only after the first byte from sshd.** An SSH server speaks first. Before sshd is up,
   the proxy answers "connection refused", and Packer retries until `ssh_timeout`. An early success would turn a
   booting guest into a failed handshake, and Packer stops after `ssh_handshake_attempts` of those.
3. **The controller names the candidate.** The proxy must know the VM before Packer clones it. So the controller
   passes `AIR_IMAGE_CANDIDATE_VM` and `AIR_IMAGE_SSH_PROXY_PORT` to
   [build-golden.sh](../../../../provision/scripts/build-golden.sh). The script refuses to run without both values,
   and refuses a name that is not a candidate of its `GOLDEN_VM`.
4. **The template has a required `ssh_proxy_port` variable.** `static-validate.sh` passes a placeholder, because a
   validation connects to nothing.

## Alternatives rejected

- **The System Settings grant.** A person must grant each app on each Mac, and the grant follows the app, not the
  controller.
- **The `defaults write` allow-list of Tart.** It needs `sudo` and a reboot on every host.
- **An `ssh -L` tunnel through `/usr/bin/ssh`.** It rests on an Apple exemption for its own binaries, and it needs a
  key in each guest.
- **A local TCP listener per worker.** It adds a port and a lifetime to manage, and the HTTP client gains nothing.
- **A Packer bastion through `/usr/bin/ssh`.** The Packer bastion is its own Go client, so the Apple exemption does
  not apply.
- **A plain loopback port forward for Packer.** The plugin overwrites `ssh_host`, so Packer still connects to the
  guest address.

## Consequences

- No Mac needs a prompt, a grant or a reboot. The lane and the image build run under any app.
- Each pooled HTTP connection and each SSH connection costs one exec spawn. A reused connection costs nothing more.
- No host process and no LAN peer can reach the daemon port. Only a process in the guest can, and the token refuses
  it.
- `AIR_VM_NETWORK` sets only the internet access of the guest. Every network mode gives the daemon the same path.
- `build-golden.sh` runs only through the controller.
- The guest address of the image build still comes from `tart ip`, which reads the DHCP leases of the host.
