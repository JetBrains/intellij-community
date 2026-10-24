# yonderd

The agent of yonder on a host: macOS and Linux. `crates/yonder-link/API.md` lists the codes of `Reply::Refused`.

## The commands

| Command | Output |
|---|---|
| `serve [--port 7741]` | One JSON line `{"listening": "<address>", "fingerprint": "<64 hex>"}`, then the log on stderr. |
| `supervise <run dir> [--lock-fd <descriptor>]` | Nothing. `serve` starts it for each `Start` with the flock of the run, and it writes `exit.json` in the run directory. |
| `setup --client <fingerprint> --name <name> [--port 7741]` | One JSON line `{"fingerprint": "<64 hex>", "port": 7741, "launchd": "bootstrapped" \| "restarted" \| "none"}`. |
| `version` | One JSON line `{"version": "<cargo version>", "schema_version": 2}`. |

A command that refuses prints one JSON line `{"refused": {"code": "<code>", "message": "<text>"}}` on stderr and
exits with 1. A usage error exits with 2.

| Input | Code |
|---|---|
| A command line that clap refuses, such as a fingerprint that is not 64 hex digits | exit 2, no JSON |
| No `HOME` | `agent_error` |
| An identity, an allow list, a plist, a lease file or a port that the agent cannot read or write | `agent_error` |
| A failed `launchctl print`, `bootstrap`, `bootout` or `kickstart` | `launchd_failed` |

`setup` moves each lease file of `~/.cache/bt-remote/leases/` into `~/.cache/yonderd/leases/` when the agent has no
file of that holder.

## The requests

| Input | Code |
|---|---|
| A first request that is not a hello, or a second hello | `bad_request` |
| A commit that is not 40 or 64 lower-case hex digits | `bad_request` |
| More than 1024 commits in one `Have` | `bad_request` |
| A pack ref that is not `refs/yonder/<run id>` or `refs/yonder/base/<name>`, where a name has the rules of a run id | `bad_request` |
| A start ref that is not `refs/yonder/<run id>` | `bad_request` |
| A holder that cannot name a file (`[0-9A-Za-z._-]+`, no leading dot) | `bad_request` |
| A start with no command, or a fetch directory with a root, `.` or `..` | `bad_request` |
| A follow offset that is not 0 or the offset after a line end of the log | `bad_request` |
| A fetch or a forget of a run that goes on | `bad_request` |
| A lease request for a holder whose lease file names another repository | `lease_failed`, and the file stays |
| A run whose commit the repository lacks, and whose start ref does not come within 30 min | `pack_missing`, as the end of the run |

- A `Lease` or `Release` waits up to 50 s for a run of the same holder, then refuses with `lock_timeout`. A run waits
  up to an hour.
- `Lease` makes no idle sweep, because the request has no idle hours. Only a run returns the idle leases of other
  holders.
- `Have`, `Lease`, `Release` and the end of `Pack` send `Reply::Heartbeat` while they wait.
- A `Start` can come before the `Pack` of its ref. The run takes the lock and the lease, then waits for the pack
  before the checkout. A commit that the repository has already needs no pack. Else the wait ends when the start ref
  exists. A wait longer than 1 s writes the line `yonder: waiting for the pack of <commit>` to the log. A cancel ends
  the wait too.
- A follow of an ended run sends `Reply::Phases` right before its `Reply::Exit` or its `Reply::Refused`. `Runs` shows
  the phases that have ended so far. The phases are `lock`, `sweep`, `lease`, `pack_wait`, `checkout`, `command` and
  `stage`, in this order. A run that ends early has only the phases up to its last step.
- A follow sends its heartbeats from a thread of their own, so a slow read of the run files holds none back. On macOS a
  kqueue tells the follow about a change of the log or of the run directory at once. On Linux the follow reads the
  run files every 200 ms.
- The cleanup never deletes a base ref `refs/yonder/base/<name>`. The next pack to the same name replaces it.
- `Cancel` writes the cancel request of the run and answers at once. A run that its supervisor lost ends at the next
  `Cancel`, `Follow` or cleanup, with `agent_error`.
- A refused `Start` deletes the ref of its pack, unless a kept run has the same id.
- A run that ends with a refusal shows the exit code 255 in `Runs`. A run that a cancel ends before its command starts
  ends with the exit code 130.
- A TLS handshake has 10 s in all. At most 32 handshakes go on at a time, and the agent closes a socket over that cap
  at once.

## The files

Each directory of the agent has the mode 0700, and each file that it writes has the mode 0600.

## Upgrades

On Linux, a host needs a restart of `serve` after an upgrade of the binary. `serve` starts each supervisor from its own
executable, and Linux names a replaced executable `(deleted)`.

## The test durations

A process test shortens these durations through the environment of `serve`, in milliseconds:

| Variable | Duration | Default |
|---|---|---|
| `YONDERD_TEST_GRACE_MS` | from SIGINT to SIGKILL of a cancelled command | 120 s |
| `YONDERD_TEST_HANDSHAKE_MS` | the total deadline of a TLS handshake | 10 s |
| `YONDERD_TEST_HEARTBEAT_MS` | between two heartbeats | `HEARTBEAT_SECONDS` |
| `YONDERD_TEST_PACK_WAIT_MS` | the wait of a run for its pack | `PACK_WAIT_SECONDS` |

`YONDERD_TEST_FOLLOW_HOLD` names a file. While the file exists, each follow waits before its next read of the run
files. A test holds the follow thread with it, to check that the heartbeats go on.
