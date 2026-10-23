# yonder-link

## The frames

A frame is a kind byte, a big-endian u32 length, and the payload. A message is one JSON value of at most 1 MiB. A byte
chunk is at most 64 KiB. An empty chunk keeps a byte stream alive while its sender has no bytes yet. An end frame
completes a byte stream.

| Input | Refusal |
|---|---|
| A frame of an unknown kind | `LinkError::Protocol` |
| A frame over its limit | `LinkError::Protocol` |
| A message that is not a request or a reply of `proto` | `LinkError::Protocol` |
| Bytes where a message is due, or a message inside a byte stream | `LinkError::Protocol` |
| No frame before the deadline of the socket | `LinkError::TimedOut` |
| A certificate that the pin or the allow list does not name | `LinkError::Untrusted` |
| An allow list line that is not `<64 hex digits> <name>` | an `InvalidData` error that names the file and the line |

## The refusal codes of the agent

`Reply::Refused` carries one of these codes. A client branches on the code, never on the message.

| Code | Meaning |
|---|---|
| `schema` | The hello names another schema version. |
| `bad_request` | A request that is not due here, or a field that the agent does not accept. |
| `bad_run_id` | A run id that cannot name a directory. |
| `run_exists` | A start with the id of a kept run. |
| `unknown_run` | No kept run has the id. |
| `repo_missing` | The repository path is not a git repository on the host. |
| `pack_failed` | `git index-pack` or `git update-ref` failed. |
| `lock_timeout` | Another run of the holder held the lease too long: an hour for a run, 50 s for a lease or a release. |
| `lease_failed` | `bd lease get` or `bd lease return` failed. |
| `pack_missing` | The commit of a run did not come in a pack within `PACK_WAIT_SECONDS`. |
| `checkout_failed` | The commit cannot be checked out in the slot. |
| `spawn_failed` | The command cannot start. |
| `agent_error` | The agent cannot read or write its own files. |
| `bad_config` | `.yonder.toml` of the commit cannot be read, or it has an unknown key or a value of the wrong shape. |
| `prepare_failed` | A prepare step of `.yonder.toml` exited with a code other than 0, so the command did not run. |
| `bad_binary` | The binary of an upgrade has another size or SHA-256 than its request, or `<binary> version` does not answer as an agent of this schema with the feature `upgrade`. |
| `upgrade_failed` | The agent cannot stage, keep or swap its binary, or start the watchdog: a host without launchd, no LaunchAgent, a failed file call, or a second upgrade at the same time. |

## Compatibility

A change that an older end cannot read raises `SCHEMA_VERSION`. Each field after the first fields of schema 2 is
optional: `RunSpec::nice`, `outputs` and `changes`, `Reply::Hello::features` and `binary_sha256`, and `Reply::File::kind`. An older client
does not send them, and an older agent ignores them. So an agent lists its features in its hello (`proto::feature`), and
a client checks a feature before it relies on its field. An agent sends a file of another kind than
`FileKind::Staged` only to a client that asked for outputs or changes, so an older client never gets one. An older agent
cannot read `Request::Upgrade` and closes the connection, so a client sends it only to an agent with the feature
`upgrade`. The SHA-256 of the binary names a build, because the version of the crate is the same for each build.
