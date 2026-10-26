# yonder-client

The client of yonder, for `yonder` and `bt --host`. `Client` holds the configuration directory, the seam of the local
git and ssh calls (`Processes`), the sink of the stderr lines, the sink of the stdout lines of a run
(`Client::with_stdout`; without it they come in `RunOutcome::stdout`), and the Ctrl+C question. Each operation opens a
new connection per request: `run`, `run_with`, `attach`, `cancel`, `runs`, `lease`, `release`, `hello`, `prepare` and `upgrade`.

## The options of a run

`Client::run` is the run of `bt --host`. `Client::run_with` takes `RunOptions`, and the `yonder run` CLI sets each one:

| Option | `bt --host` | `yonder run` | Effect |
|---|---|---|---|
| `working_tree` | off | on, off with `--head` | The run ships the working tree: HEAD plus the tracked and untracked changes that git does not ignore, as one commit. Off, a checkout with local changes is refused with `local_changes`. |
| `nice` | none | `--nice`, 10 by default | The niceness of the prepare steps and the command on the host. So the runs of `bt --host` go first. |
| `outputs` | off | on | The outputs that `.yonder.toml` names come back, each in place of its local copy. |
| `apply` | off | `--apply` | The changes that the command made to the files come back as a patch, and `git apply` applies it to the working tree. |
| `repo_by_name` | off | on without `--repo` | The run goes to the repository of the same name as the local one beside the repository of the host file (`~/projects/branch-deck` beside `~/projects/idea-1`), or to the one of the host file when the host lacks it. `Client::lease_for` and `release_for`, and so `yonder lease` and `yonder release`, take the same repository. |

### The commit of the working tree

`git status --porcelain -z` first: a checkout without changes ships HEAD. Else the client copies the index of the
checkout to the temporary directory, runs `git add --all` of the paths that the status named and `git write-tree` on the
copy (`GIT_INDEX_FILE`), and writes a commit on HEAD with `git commit-tree --no-gpg-sign`. A staged deletion and the old
path of a staged rename are no paths to add: the index copy has them already, and git refuses a path that neither the
index nor the files have. An intent-to-add, a deletion in the files and a gitlink go in as `git add --all` takes them. So the cost is one status and
the changed files: about 4 s in a checkout of the monorepo with 1.4M files, where a second scan of every file took 30 s. Its author and committer are `yonder <yonder@yonder.invalid>` at the date of
the committer of HEAD, and its message is `yonder: the working tree on <HEAD>`. So the same tree on the same HEAD is the
same commit, and a second run of it sends no pack. The index, the files and the refs of the checkout stay as they are,
and no ref names the commit: its objects are loose objects that `git gc` prunes. Ignored files stay local, and the host
has its own.

### The changes

With `apply`, the patch goes to `yonder/<run>.patch` in the git directory of the checkout (`git rev-parse --git-path
yonder`), and a patch older than 7 days there goes. `git apply --whitespace=nowarn` applies it to the files of the
working tree, not to the index. A patch that does not apply, such as after a local edit of the same lines, refuses with
`apply_failed` and names the patch, and the working tree stays as it was. `yonder fetch` of such a run writes the patch
there too.

## The files

| File | Content |
|---|---|
| `~/.config/yonder/identity.crt`, `identity.key` | The certificate and the key of the client, created on the first call. |
| `~/.config/yonder/hosts/<alias>.json` | `{"alias", "fingerprint", "port", "hostname", "addresses", "repo"}`. `prepare` writes it. A connection puts the address that answered first, and a cached address that failed last. An address of the host name replaces the cached ones, which all failed. |
| `~/.config/yonder/bases/<alias>/<holder>` | The commit of `refs/yonder/base/<holder>` on the host, after the last base sync. The next run adds it to the candidates of `Have`. |

## The refusals

A refusal carries an `Exit`, which is the exit code of `yonder`:

| Exit | Code | When |
|---|---|---|
| `NotRun` | 75 | A run refused before its command: the host did not run it, so the caller can run it locally instead. Every refusal before the start, a pack that did not reach the host, and a refusal of the agent whose phases have no `command`, such as `lease_failed`, `prepare_failed` or `bad_config`, but `unknown_run`. |
| `Interrupted` | 130 | Ctrl+C before the command, or a second Ctrl+C during the follow. |
| `Refused` | 255 | Any other refusal: the link broke after the start and the run can go on on the host, the run vanished on the host after its start (`unknown_run`: a `yonder forget` or the expiry removed it, so whether its command ran is unknown, and the client forgets nothing), the changes did not apply, or a request other than a run failed. |

A refusal of the agent keeps the code of the agent: the `API.md` of `yonder-link` lists those codes. The client adds
these:

| Code | Input |
|---|---|
| `usage` | An alias that is not `[0-9A-Za-z._-]+`, a run without a command, or `prepare` without `--agent` outside a checkout of the monorepo. The last message names `--agent`. For a bootstrap of `upgrade`: no checkout, a `yonder-upgrade/` that is there already, or one that git ignores. |
| `config` | No home directory, an identity that cannot be read or created, or a file that cannot be written. |
| `no_host` | No host file for the alias. The message names `bt --host <alias> --prepare` and `yonder prepare`. |
| `bad_host_file` | A host file that is not JSON of the host file shape, or that names another alias. |
| `unreachable` | No cached address and no address of the host name answers. The message names each address and the lookup. On macOS it also names Local Network privacy when an address of the local network failed with `EHOSTUNREACH` and the relay through `/usr/bin/nc` cannot pass the refusal. |
| `untrusted` | The agent shows a certificate that the host file does not pin, or it refuses the certificate of the client. |
| `schema` | The agent speaks another schema version. |
| `timed_out` | No reply within the deadline of the request. For `Follow`: no reply for 10 min, over all reconnects. |
| `link` | The connection closed, or a reply came that the protocol does not allow there, such as `Phases` outside a `Follow`. |
| `local_changes` | The checkout has tracked or untracked changes. |
| `behind` | The host has none of the candidate commits: HEAD, its first-parent ancestors at the distances 1, 2, 4 … 65536, and the merge bases with `master` and `origin/master`. The first `Have` names the distances up to 16, and only a host that has none of them gets the rest in a second `Have`. The message gives the commit count of the history and names `bt --host <alias> --prepare`. |
| `fetch_failed` | Fetch did not copy every staged file. The run stays on the host for `yonder fetch` and `yonder forget`. |
| `git_failed` | A local git call failed or ran past its deadline. |
| `ssh_failed` | An ssh call of `prepare` failed or ran past its deadline of 30 s (120 s for the copy). |
| `agent_failed` | The agent binary cannot be read, its Bazel build failed or wrote nothing for 30 min, `cquery` named no `yonderd` file, `yonderd setup` failed on the host, or it printed no setup line. For `upgrade`: a host that is not macOS, a binary of another platform than the hello names, a bootstrap run that failed or printed no line of `yonderd upgrade`, or an agent that answers with a third binary at the end. |
| `rolled_back` | The new agent of `upgrade` did not answer in time, and the previous binary answers: the watchdog brought it back. The message names `~/.cache/yonderd/upgrade.json` and the log of the agent. |
| `interrupted` | Ctrl+C came before the run started, or a second Ctrl+C stopped the follow. A press after the start cancels the run, and the run then ends with the code of the host. |
| `old_agent` | The run needs a feature that the agent lacks, such as the changes of `apply`. The message names `yonder prepare`. An option that an older agent would ignore, such as `nice`, `.yonder.toml` or the login environment, only gets a `yonder: warning:` line. |
| `apply_failed` | The changes of the command do not apply to the working tree, or they did not come because the fetch failed. The message gives the exit code of the command and the patch file, or `yonder fetch`, which writes the patch into the git directory of the checkout. |

A fetched path that is absolute, has `..` or `\`, is not below a `fetch` directory or its output, or is inside a `.git`
directory in any case (`.GIT`, `.git.`, `git~1`) is skipped, and the fetch then fails. The first file of an output
removes the local file or directory of the output. `yonder fetch` without `--fetch` takes the `fetch` directories of
the run from `Runs`. A run that names none can still bring outputs and changes, and a staged file of it is skipped.
Each file goes to `<name>.yonder-part` first and takes its name only at the size that the agent named. The run
removes itself on the host (`Forget`) only when the client saw its end and every file came.

A pack goes to a temporary file first, under `git pack-objects --progress` with a quiet limit of 60 s and no limit on
the whole pack. Each write to the agent has a deadline of 120 s.

## The agent of `prepare`

`yonder prepare <alias>` without `--agent` builds the agent in the root of the checkout of the working directory. An
ultimate root, with `community/tools/yonder`, builds `@community//tools/yonder:yonderd`. A community root builds
`//tools/yonder:yonderd`. The build is `./bazel.cmd build <label>` under a quiet limit of 30 min, as in
`bt --host --prepare`. Then `./bazel.cmd cquery --output=files <label>` names the binary relative to the execution
root, and `./bazel.cmd info execution_root` names that root. The monorepo has no `bazel-out` link in the checkout.

## The upgrade

`Client::upgrade` updates the agent of a macOS host over the link:

1. The hello names the platform, the features and the SHA-256 of the binary of the agent. A host that is not macOS
   refuses with `agent_failed` and names `yonder prepare`.
2. The local binary is `--agent`, or the build of `@community//tools/yonder:yonderd` as for `prepare`. A binary of
   another platform than the hello refuses before it goes: the client reads the Mach-O or ELF header. The binary that
   the agent runs already is current, and nothing goes, unless `--force`.
3. An agent with the feature `upgrade` gets `Request::Upgrade` with the binary, and `force` with `--force`.
4. An agent without the feature gets a bootstrap run, once. The binary goes to `yonder-upgrade/yonderd` of the working
   tree. That path must be new, and git must not ignore it, so the commit of the working tree carries it. The run in a
   slot of the repository of the host file gives the binary the mode 0755, because a client on Windows writes no mode,
   and runs `yonder-upgrade/yonderd upgrade yonder-upgrade/yonderd`: the new binary installs itself with the checks and the watchdog of the request, and
   restarts the agent. The follow of the run connects again to the new agent. The line of `yonderd upgrade` in the
   stdout of the run names the new binary. The directory goes after the run, also after a failure. Run it in a
   checkout that no other session uses, because each run of that checkout ships the directory meanwhile.
5. The client connects again, every 1 s to 5 s, until the hello names the new binary, up to 110 s: the drain of the old
   agent (20 s), the wait of the watchdog (60 s) and 30 s for launchd. The previous binary at the end refuses with
   `rolled_back`, no answer with `unreachable`.

## The order of a run

1. The commit: `git rev-parse HEAD`, then `git status` and `Have` at the same time; local changes refuse at once. With
   `working_tree`, the commit of the working tree first, then `Have`. A host that has none of the candidates refuses
   with `behind`. The hello of the agent names its features, so a run that needs one that the agent lacks refuses here.
   Ctrl+C up to here runs nothing on the host.
2. `Start`. The host takes the lock and the lease at once. A start whose reply does not come is cancelled, and the
   run refuses with `link` or `timed_out`.
3. `Follow` on a thread of its own, up to `Phases` and `Exit`. The lines of the host show while the pack builds.
4. `Pack` on a connection of its own: the client opens it, builds the pack file meanwhile with an empty chunk every
   15 s, then sends the file. When the pack fails, the client cancels the run, stops the follow, and refuses with the
   code of the pack: `link`, `timed_out`, `git_failed`, or the code of the agent. The message says whether the cancel
   came.
5. A thread syncs the base: a thin pack of the local `origin/master`, or of `master`, to `refs/yonder/base/<holder>`.
   It goes once per run, after the pack of step 4. It skips the pack when the host has the commit. A failure is one
   `yonder: warning:` line on the stderr, and the run goes on. The end of the run waits for the sync, and Ctrl+C
   closes its connection.
6. `Fetch` and `Forget`. A run with `outputs` or `apply` fetches even without a `fetch` directory.
7. With `apply`, `git apply` of the patch.

Ctrl+C after the start, the build of the pack included, cancels the run on the host: the first press sends `Cancel`
and stops the pack, and the output follows up to the end of the run. The second press stops the follow at once.

## The steps

`RunOutcome.phases` lists the steps of a run with their durations: `pack` and `upload` of the client, the steps of the
host from `Reply::Phases`, `fetch`, and `total`, the whole call. The line of a started run is `yonder: run <id> of
<commit> on <alias>`, with ` in <repository>` when the repository is not the one of the host file, and `: the working
tree, HEAD <commit> and <n> changed paths` for a commit of the working tree. `phases_line` makes the stderr line `yonder: phases:
pack 3.1s, upload 2.0s, lease 9m02s, … total 10m13s`. `yonder run` and `bt --host` print it at the end of a run. A run
that the agent refuses has no outcome, so the client says that line itself. `yonder runs` shows the steps of 0.1 s or
more of each run.
