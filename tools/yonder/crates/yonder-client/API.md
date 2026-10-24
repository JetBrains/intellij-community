# yonder-client

The client of yonder, for `yonder` and `bt --host`. `Client` holds the configuration directory, the seam of the local
git and ssh calls (`Processes`), the sink of the stderr lines, and the Ctrl+C question. Each operation opens a new
connection per request: `run`, `attach`, `cancel`, `runs`, `lease`, `release`, `hello` and `prepare`.

## The files

| File | Content |
|---|---|
| `~/.config/yonder/identity.crt`, `identity.key` | The certificate and the key of the client, created on the first call. |
| `~/.config/yonder/hosts/<alias>.json` | `{"alias", "fingerprint", "port", "hostname", "addresses", "repo"}`. `prepare` writes it. A connection puts the address that answered first. |

## The refusals

Each refusal leaves `yonder` by 255. A refusal of the agent keeps the code of the agent: the `API.md` of `yonder-link`
lists those codes. The client adds these:

| Code | Input |
|---|---|
| `usage` | An alias that is not `[0-9A-Za-z._-]+`, or a run without a command. |
| `config` | No home directory, an identity that cannot be read or created, or a file that cannot be written. |
| `no_host` | No host file for the alias. The message names `bt --host <alias> --prepare` and `yonder prepare`. |
| `bad_host_file` | A host file that is not JSON of the host file shape, or that names another alias. |
| `unreachable` | No cached address and no address of the host name answers. The message names each address and the lookup. |
| `untrusted` | The agent shows a certificate that the host file does not pin, or it refuses the certificate of the client. |
| `schema` | The agent speaks another schema version. |
| `timed_out` | No reply within the deadline of the request. For `Follow`: no reply for 10 min, over all reconnects. |
| `link` | The connection closed, or a reply came that the protocol does not allow there. |
| `local_changes` | The checkout has tracked or untracked changes. |
| `behind` | The host has none of the candidate commits: HEAD, its first-parent ancestors at the distances 1, 2, 4 … 65536, and the merge bases with `master` and `origin/master`. The message gives the commit count of the history and names `bt --host <alias> --prepare`. |
| `fetch_failed` | Fetch did not copy every staged file. The run stays on the host for `yonder fetch` and `yonder forget`. |
| `git_failed` | A local git call failed or ran past its deadline. |
| `ssh_failed` | An ssh call of `prepare` failed or ran past its deadline of 30 s (120 s for the copy). |
| `agent_failed` | The agent binary cannot be read, `yonderd setup` failed on the host, or it printed no setup line. |
| `interrupted` | Ctrl+C came before the run started, or a second Ctrl+C stopped the follow. |

A fetched path that is absolute, has `..` or `\`, or is not below a `fetch` directory is skipped, and the fetch then
fails. Each file goes to `<name>.yonder-part` first and takes its name only at the size that the agent named. The run
removes itself on the host (`Forget`) only when the client saw its end and every file came.

A pack goes to a temporary file first, under `git pack-objects --progress` with a quiet limit of 60 s and no limit on
the whole pack. Each write to the agent then has a deadline of 60 s.
