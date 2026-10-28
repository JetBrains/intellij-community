# yonder-link

## The frames

A frame is a kind byte, a big-endian u32 length, and the payload. A message is one JSON value of at most 1 MiB. A byte
chunk is at most 64 KiB. An empty chunk keeps a byte stream alive while its sender has no bytes yet. An end frame
completes a byte stream.

A sender collects its frames in a buffer, and writes the buffer to TLS in one call when it holds 64 KiB, at a flush,
before a read and at the close. `send`, `send_all`, `send_end`, `send_keepalive` and `send_stream` flush. `queue`,
`queue_stream` and `send_bytes` do not, so a reply of many small files, such as a fetch, takes a few TLS records.

| Input | Refusal |
|---|---|
| A frame of an unknown kind | `LinkError::Protocol` |
| A frame over its limit | `LinkError::Protocol` |
| A message that is not a request or a reply of `proto` | `LinkError::Protocol` |
| Bytes where a message is due, or a message inside a byte stream | `LinkError::Protocol` |
| No frame before the deadline of the socket | `LinkError::TimedOut` |
| A certificate that the pin or the allow list does not name | `LinkError::Untrusted` |
| An allow list line that is not `<64 hex digits> <name>` | an `InvalidData` error that names the file and the line |
| A client name with a control character, such as a line break, in `AllowList::add` | an `InvalidInput` error that names the name |

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
| `lock_timeout` | A run got no host slot within an hour, or an upgrade of git did not end in 91 min. |
| `lease_failed` | `bd lease host lend` failed, or the bd of the host lacks it. |
| `pack_missing` | The commit of a run did not come in a pack within `PACK_WAIT_SECONDS`. |
| `checkout_failed` | The commit cannot be checked out in the slot. |
| `spawn_failed` | The command cannot start. |
| `agent_error` | The agent cannot read or write its own files. |
| `bad_config` | `.yonder.toml` of the commit cannot be read, or it has an unknown key or a value of the wrong shape. |
| `prepare_failed` | A prepare step of `.yonder.toml` exited with a code other than 0, so the command did not run. |
| `old_git` | The git of the host is older than the minimum of yonder, and the agent cannot upgrade it: no Homebrew, or a failed upgrade. The message says what the user installs. |
| `bad_binary` | The binary of an upgrade has another size or SHA-256 than its request, or `<binary> version` does not answer as an agent of this schema with the feature `upgrade`. |
| `plist_changed` | The new binary of an upgrade would write another LaunchAgent than the one on the host: `yonder prepare` installs it. |
| `downgrade` | The new binary of an upgrade has a lower version than the agent, and the request has no `force`. |
| `upgrade_failed` | The agent cannot stage, keep or swap its binary, or start the watchdog: a host without launchd, no LaunchAgent of the shape of `setup`, a failed file call, a watchdog of an earlier upgrade that still lives, or a second upgrade at the same time. |

## Compatibility

A change that an older end cannot read raises `SCHEMA_VERSION`. The client and the agent are always replaced together,
so the protocol keeps no field for an older end, with one exception: the upgrade. An agent lists its features in its
hello (`proto::feature`), with the SHA-256 of its binary. An agent without the feature `upgrade` cannot read
`Request::Upgrade` and closes the connection, so a client sends the request only to an agent with the feature. The
hello, `Request::Upgrade`, `Reply::Upgraded`, the line of `yonderd version` and `yonderd check`, and the LaunchAgent
keep their shapes, so the agent of a host takes the next binary over the link. The SHA-256 of the binary names a
build, because the version of the crate is the same for each build.
