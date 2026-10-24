# yonder-link

## The frames

A frame is a kind byte, a big-endian u32 length, and the payload. A message is one JSON value of at most 1 MiB. A byte
chunk is at most 64 KiB. An end frame completes a byte stream.

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
