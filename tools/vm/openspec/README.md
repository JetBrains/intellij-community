# UI-lane decisions

This folder is the OpenSpec root of the UI-lane tooling in `community/tools/vm`.
It has no spec yet. The specs of the controller and the recorder stay under [`spec/`](../spec/README.md) in the format `plain-1`.
The root holds the decisions of the workspace. A decision is the `design.md` of a change under `changes/<change>/`.
After the archive, `changes/archive/<YYYY-MM-DD>-<id>/design.md` is the decision record.
The folder [`docs/decisions/`](../docs/decisions/README.md) holds the older records and takes no new record.
The format and the change workflow are in [`SPEC_GUIDE.md`](../../../.ai/spec/SPEC_GUIDE.md#openspec-roots), section "OpenSpec roots". The rules of this root are in [`config.yaml`](config.yaml) and in [`community/tools/vm/AGENTS.md`](../AGENTS.md).

## The gate

Run the OpenSpec CLI from `community/tools/vm`:

```sh
(cd community/tools/vm && ../../../tools/openspec.cmd validate --all --strict)
(cd community/tools/vm && ../../../tools/openspec.cmd validate --archived --strict)
```
