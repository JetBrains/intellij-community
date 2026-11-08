---
topic: testing
---

# The image build reaches SSH through the exec channel

Date: 2026-11-08

## Status

Accepted. It extends [ADR 0182](0182-the-daemon-is-reached-through-the-exec-channel.md) from the lane daemon to the
Packer SSH of `image build`. The proxy is `crates/avl-vm/src/image/ssh_relay.rs`, and the
[provisioning README](../../provision/README.md) describes the build.

## Context

`vm.cmd --backend tart image build` runs `provision/scripts/build-golden.sh`, and the script runs Packer with the
Tart plugin 1.21.0. The plugin asks `tart ip` for the address of the candidate, and Packer's SSH communicator connects
to port 22 there. Every provisioner of `air-macos.pkr.hcl` runs over that connection.

On 2026-11-08 a build from a background agent session timed out with "Timeout waiting for SSH" after 5 minutes. In the
candidate, sshd ran, Remote Login was on and the firewall was off, and `tart exec` worked. Every connect from the
session to a `192.168.64.*` or other LAN address failed at once with `EHOSTUNREACH`. This is the Local Network privacy
refusal of ADR 0182: the grant follows the app that runs the controller, and this app had none.

ADR 0182 moved the daemon connection to the exec channel, and the image build was the one remaining user of the guest
network. Packer takes no custom transport. Its communicator has two seams for the connection to the address it
resolved: a SOCKS5 proxy (`ssh_proxy_host`, `ssh_proxy_port`) and an SSH bastion. In the Packer SDK 0.6.10, the
`Host` function of the plugin overwrites `ssh_host`, so a loopback `ssh_host` alone is not one of them.

## Decision

1. **`image build` serves Packer a SOCKS5 proxy on a loopback port, and every connection of the proxy is one
   `tart exec -i <candidate> /usr/bin/nc 127.0.0.1 22`.** The bytes reach the sshd of the candidate over vsock. The
   proxy takes only `CONNECT` with no authentication, and only to port 22. It does not read the address that Packer
   asks for, because the proxy serves the one candidate of this build. It lives as long as the delegate runs.
2. **The proxy answers success only after sshd sent its first byte.** An SSH server speaks first. Before the guest
   agent and sshd are up, the exec child ends without a byte, and the proxy answers "connection refused". Packer
   retries a refused connect until `ssh_timeout`. A success before the byte would turn a booting guest into a failed
   handshake, and Packer gives up after `ssh_handshake_attempts` of those.
3. **The controller names the candidate.** The proxy must know the VM before Packer clones it. So the controller
   passes `AIR_IMAGE_CANDIDATE_VM` and `AIR_IMAGE_SSH_PROXY_PORT` to `build-golden.sh`. The name is
   `<GOLDEN_VM>-candidate-<UTC time>-<pid>`, with `GOLDEN_VM` from `versions.env`, as before. The script refuses to
   run without both values, and it refuses a name that is not a candidate of its `GOLDEN_VM`.
4. **The template has a required `ssh_proxy_port` variable.** `static-validate.sh` passes a placeholder, because a
   validation connects to nothing.

These tests pin the decision: `crates/avl-vm/src/image/ssh_relay/tests.rs`, which covers the bytes both ways, a
refused first attempt followed by a retry, an exec channel that cannot start, a port that is not sshd, and a client
without an acceptable method.

## Consequences

- **An image build needs no Local Network grant.** It runs under any app, as the lane does.
- **`build-golden.sh` runs only through the controller.** The provisioning README already names the controller as
  the only entry point.
- **Each SSH connection costs one exec spawn.** Packer opens one connection for the whole build, and one more for
  each retry while the guest boots.
- **The guest address still comes from `tart ip`.** The plugin resolves it before it connects. `tart ip` reads the
  DHCP leases of the host and needs no network permission.

## Alternatives rejected

- **The System Settings grant, or Tart's `defaults write` allow-list.** ADR 0182 rejected both for every QA machine.
- **A bastion through `/usr/bin/ssh`.** Packer's bastion is its own Go SSH client, not Apple's binary, so the
  exemption that ADR 0182 names does not apply to it.
- **A plain loopback port forward.** Packer would still connect to the address that `tart ip` names, because the
  plugin overwrites `ssh_host`.
