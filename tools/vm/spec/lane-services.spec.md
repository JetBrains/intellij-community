---
name: The Lane Services
description: Requirements for the lane services that a lane IDE talks to instead of production, and for the control route of each service.
style: plain-1
targets:
  - ../crates/avl-guest/src/lane.rs
  - ../crates/avl-guest/src/lane/package.rs
  - ../crates/avl-guest/src/lane/services.rs
  - ../crates/avl-guest/src/lane/services/*.rs
  - ../crates/avl-guest/src/lane/services/cloud/*.rs
  - ../crates/avl-guest/src/lane/services/cloud/spawner/*.rs
  - ../crates/avl-guest/testdata/lane-services/*
  - ../../../../plugins/air/tests/integration/bridge/resident/tests/AirLaneService*.kt
---

# The Lane Services

Status: Draft
Date: 2026-10-14

## Summary

A lane IDE must reach no production service. So the guest agent serves four lane services on the guest loopback. They
are the fake ACP registry, the JetBrains Cloud emulator, the feedback capture and the Codex mock model. Each service also
has a control route. An in-IDE scenario body uses that route to arm a fault or to read what the IDE sent.

[The UI-lane Controller](lane-controller.spec.md) specifies the guest agent.
[Flow UI Scenarios](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md) specifies the scenario programs.

## Goals

- A scenario body steers each service and reads its record through one protocol.

## Non-goals

- Choosing the ports and the token. The lane identity derives them.
- Starting and stopping the services. The lane daemon owns them.

## Requirements

### Every service

- A service binds `127.0.0.1` on its given port. The port 0 binds any free port.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- A service refuses a port in use with the code `lane_service_bind`.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- A stopped service frees its port, and every answer that waits ends.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/feedback/tests.rs

- A service reads a request body with a `Content-Length` or in the chunked encoding.
  [@test] ../crates/avl-guest/src/lane/services/http/tests.rs

- A service answers `Expect: 100-continue` with the interim answer before it reads the body.
  [@test] ../crates/avl-guest/src/lane/services/http/tests.rs

- Every answer has `Connection: close`, and the service closes the connection after it. A WebSocket upgrade is the
  one exception.
  [@test] ../crates/avl-guest/src/lane/services/http/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- An answer body has a `Content-Length`. Only the event stream of the Codex mock model is chunked.
  [@test] ../crates/avl-guest/src/lane/services/http/tests.rs

- A malformed request gets 400, and a body of more than 64 MiB gets 413.
  [@test] ../crates/avl-guest/src/lane/services/http/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

### The control route

The service names of the control route are `registry`, `cloud`, `feedback` and `codex`. The lane-service token is the
lane secret with the purpose `air-lane-services`, and the IDE reads it from `lane-services.json`.

- A control request uses the path `/_lane/<service>/<route>` on the port of that service.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- A control request carries the lane-service token in the header `X-Air-Lane-Service-Token`.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- A missing or wrong token gets 401 with the code `token_refused`. The service checks the token first.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- Every control answer is JSON. A refusal is `{"error": {"code": ..., "message": ...}}`.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- An unknown route gets 404 `unknown_route`. A known route with another method gets 405 `method_not_allowed`.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- A body that is no valid JSON, or that has an unknown field, gets 400 `bad_request`.
  [@test] ../crates/avl-guest/src/lane/services/control/tests.rs

- A control request enters no record of its service.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- A mark is the number of entries in a record. A list with `since=<mark>` holds only the later entries.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

- A list without `since` starts at the first entry. A `since` that is no whole number gets 400 `bad_request`.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

- The IDE reads the token and the base URL of each service from `lane-services.json`. The reader refuses a file
  with an unknown key, another schema version or an empty token. The writer of the bridge writes the same bytes as
  the lane daemon.
  [@test] ../../../../plugins/air/tests/integration/contract/test/driver/AirLaneServicesClientTest.kt

- Each service answers the script of the golden `testdata/lane-services/control-exchanges.json` with the recorded
  status and the recorded answer.
  [@test] ../crates/avl-guest/src/lane/services/tests.rs

- One client in the IDE reads and arms the four services. It decodes every recorded answer of the golden, and its
  refusal names the service, the status and the code. A wait of the client fails at its deadline and names the wait.
  [@test] ../../../../plugins/air/tests/integration/contract/test/driver/AirLaneServicesClientTest.kt

### The fake ACP registry

The registry serves these paths to the IDE:

| path | answer |
| --- | --- |
| `/registry.json` | the agents of the lane, with one archive for each platform |
| `/packages/acp-scenario-agent-<platform>.zip` | the archive of the platform |
| any other path | 404 with an empty body |

The platforms are `darwin-aarch64`, `darwin-x86_64`, `linux-aarch64`, `linux-x86_64`, `windows-aarch64` and
`windows-x86_64`.

- The registry document of the fixture inputs is the golden `testdata/lane-services/registry.json`.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- The `sha256` of an archive in the document is the lowercase hex SHA-256 of the archive bytes.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- An archive holds the agent jar as `lib/acp-scenario-agent.jar`. A POSIX archive also holds `bin/acp-scenario-agent`.
  [@test] ../crates/avl-guest/src/lane/package/tests.rs

- The POSIX launcher starts the `java` of the distribution's JBR by its absolute path with `exec`.
  [@test] ../crates/avl-guest/src/lane/package/tests.rs

- A Windows manifest runs `java.exe` beside that `java`, with the arguments `-jar ./lib/acp-scenario-agent.jar`.
  [@test] ../crates/avl-guest/src/lane/package/tests.rs

- Every entry is stored without compression at the time 0, with no file attributes.
  [@test] ../crates/avl-guest/src/lane/package/tests.rs

- The archive bytes are the bytes that `AcpFakeAgentPackage` writes for the same jar and the same `java`.
  [@test] ../crates/avl-guest/src/lane/package/tests.rs (`the_archives_are_the_bytes_of_the_java_packer`)

- A relative `java` path or an unreadable jar is refused with the code `lane_package_input`.
  [@test] ../crates/avl-guest/src/lane/package/tests.rs

- A manifest environment starts with the two package variables. An agent value of one of them replaces it in place.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- An unknown platform gets 404 with an empty body.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

At most one distribution fault is armed. Each fault changes the answers in this way:

| fault | the answer |
| --- | --- |
| `none` | every answer is healthy |
| `registry-http-error` | the document answers the registry status with an empty body |
| `package-http-error` | an archive answers the package status with an empty body |
| `checksum-mismatch` | the document states 64 zeros as every checksum |
| `invalid-archive` | an archive is `not-a-zip-archive`, and the document states its checksum |
| `truncated-response` | an archive announces its whole length and sends only the first half |
| `slow-response` | every answer waits for the release or the end of the hold |
| `slow-package-response` | only an archive answer waits, and the document answers at once |
| `malformed-registry` | the document is text that is no JSON |

- Each fault gives the answers of the table.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

The control routes of the registry are these:

| route | method | request body | answer |
| --- | --- | --- | --- |
| `mark` | GET | none | `{"mark": n}` |
| `events?since=<mark>&prefix=<path prefix>` | GET | none | `{"mark": n, "events": [event]}` |
| `fault` | GET | none | the armed fault |
| `fault` | POST | `{"fault": id, "status": n, "holdMs": n}` | the armed fault |
| `fault` | DELETE | none | the armed fault after the clear |

An event is `{"sequence", "method", "path", "receivedAtMs", "status", "responseBytes", "truncated", "fault"}`. The
armed fault is `{"fault", "registryStatus", "packageStatus", "holdMs"}`.

- The registry logs a request when it sends the answer. A held request has no event before its release.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- The `prefix` parameter keeps only the events whose path starts with it.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- A hold is the fault `slow-package-response` with a `holdMs`. The DELETE releases every held answer.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- A hold that ends without a release still answers in full.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- `status` applies only to the two HTTP error faults. Their defaults are 503 for the registry and 404 for a package.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- `holdMs` applies only to the two slow faults. It is 1 to 600000, and the default is 30000.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- Each POST arms the whole fault. An option that the body leaves out takes its default.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

- An unknown fault gets 400 `unknown_fault`. The message lists every fault id.
  [@test] ../crates/avl-guest/src/lane/services/registry/tests.rs

### The JetBrains Cloud emulator

The emulator serves one account user with the id `air-lane-user`. A path with a trailing slash is the path without it.

| path | answer |
| --- | --- |
| `/config/JetBrainsAccount.xml` | the service configuration, with every URL on the emulator |
| `/config/JetBrainsResourceMapping.json` | `[]` |
| `/oauth2/auth` | 302 to `redirect_uri` with the code and the state |
| `/oauth2/token` | the tokens of the grant in the form body |
| `/oauth2/orgs-user-info` | a fixed token |
| `/jcp/user-management/api/ai-access-options` | the AI access options of the organization |
| `/jcp/user-management/api/org-feature-flags` | cloud tasks and AI seats on |
| `/jcp/ai-management/api/provisioning/v1/tools` | Air allowed with cloud runs |
| `/jcp/ai-management/api/provisioning/v1/gateways` | no gateway |
| `/jcp/ai-management/api/provisioning/v1/agents` | no agent |
| `/jcp/ai-management/api/provisioning/v1/acp` | 404 |
| `/jcp/ai-management/api/provisioning/v1/mcp` | 404 |
| `/jcp/repo-connections/tokens/state/meta` | the JetBrains VCS App can read and write every repository |
| `/jcp/agent-spawner/hosts/v0/remote-control/availability` | `{"available":false}`: no mobile control grant |
| `/lservice/rpc/obtainAgreement.action` | 404: the account has no agreement document |
| `/lservice/rpc/fetchData.action` | 404: the account has no revocation list and no block list |

- The configuration names the emulator for every account URL and as the JCP URL.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The emulator states three launch properties. They name the configuration and the resource mapping.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The login redirect carries the code `air-lane-authorization-code` and the state as a form value.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- A login without `redirect_uri` gets 500.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The `authorization_code` grant answers an unsigned id token. Its issuer is the base URL of the emulator.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- Another grant answers the access token `air-lane-<audience>`, or `air-lane-token` without an audience.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The AI access discovery answers one cloud workspace or no option, as the lane states the organization.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The ACP and the MCP provisioning answer 404 with an empty body. The record lists them as served requests.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The mobile control grant answers no grant, so a lane IDE keeps mobile control off. The record lists the
  request as served.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The agreement routes answer 404 with an empty body. The IDE reads the AI terms there. Five minutes after its
  start, the IDE also updates its privacy policy there. The record lists each of these requests as served.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- Any other path gets 501 with the text `the Air lane emulates no <target>`.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

The control routes of the emulator are these:

| route | method | request body | answer |
| --- | --- | --- | --- |
| `record` | GET | none | the record |
| `record` | DELETE | none | the record, then the record is cleared |
| `spawner` | GET | none | the spawner record |
| `spawner` | DELETE | none | the spawner record, then the spawner record is cleared |
| `spawner/fail` | POST | `{"count": n, "status": n}` | the spawner record |

The record is `{"discoveries": n, "served": [request], "unexpected": [request]}`, and a request is `METHOD target`.
The spawner record is `{"agentKey", "dials": [dial], "deleted": [id], "failNext": [status]}`. A dial is
`{"index", "authorization", "outcome"}`.

- The record counts each AI access discovery and lists each JCP request and each agreement request in arrival order.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The record lists each request that got 501 in arrival order.
  [@test] ../crates/avl-guest/src/lane/services/cloud/tests.rs

- The clear of `record` keeps the spawner record. The state reset of a scenario clears both records.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- `spawner/fail` takes a count of 1 to 100 and a status of 400 to 599. The default status is 401.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

### The Agent Spawner

The emulator serves the Agent Spawner of the JCP. A Cloud session of the IDE dials it as a WebSocket. The prologue of a
suite attaches the spawner to the Cloud agent of the suite, see [The UI-lane Controller](lane-controller.spec.md).

| request | answer |
| --- | --- |
| the WebSocket upgrade of `/jcp/agent-spawner/acp` with a bearer token | `101`, then the bridge to the control server |
| the same upgrade without a bearer token | 401 with an empty body |
| `DELETE /jcp/agent-spawner/sessions/<id>` | 204, and the spawner records the decoded id |
| `/jcp/agent-spawner/hosts/v0/remote-control/availability` | no grant, see the JetBrains Cloud emulator |
| any other request below `/jcp/agent-spawner/` | 501, as an unexpected request |

- The `101` answer has the accept key of RFC 6455. No extension or subprotocol is accepted.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/websocket/tests.rs

- The spawner checks only that a bearer token is present. It does not check the value.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- A forced status answers the next dial instead of the upgrade. It also answers a dial without a token.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- The spawner lists each dial in arrival order with its index, its `Authorization` header and its outcome.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- An accepted dial connects to the control server in the IDE as one agent process of the attached key.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- Each text message of the dial reaches the control server as the next `inbound` frame.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

A command of the control server acts on the dial in this way:

| command | what the spawner does |
| --- | --- |
| `write`, `writeMalformed` | it sends the line or the bytes as one text message, then it acknowledges the command |
| `closeStdout` | it reports `stdout_closed`, acknowledges the command, and closes the dial with 1000 |
| `exit` | it reports `process_exit` with the code, acknowledges the command, closes the control connection, and closes the dial with 1001 |
| `mcpCall`, `mcpListTools` | it answers an error result, because a remote spawner offers no MCP server |

- Each command has the effect of the table.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- The end of a dial before an exit reports `stdin_eof`. Then the spawner closes the control connection.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- An exit closes the control connection at once. The dial then waits at most 2 s for the answer of the IDE to its
  close. Then the connection of the dial ends, also without the answer.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- A dial before the attach closes with 1011. So does a dial that the control server refuses.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- A control connection that ends first closes the dial with 1011. So does a command of another identity.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

- A ping of the dial gets a pong with the same payload. A message is at most 16 MiB.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/websocket/tests.rs

- A frame that breaks the protocol closes the dial with 1002.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs
  [@test] ../crates/avl-guest/src/lane/services/websocket/tests.rs

- A stopped emulator ends every open dial and its control connection.
  [@test] ../crates/avl-guest/src/lane/services/cloud/spawner/tests.rs

### The feedback capture

| path | answer |
| --- | --- |
| `/feedback` | a POST queues its body as one submission and gets 200; another method gets 405 |
| `/browse` | 200 with an empty body |
| any other path | 404 |

- A submission with a length or in the chunked encoding enters the queue as its text.
  [@test] ../crates/avl-guest/src/lane/services/feedback/tests.rs

- The capture states three launch properties: the bundle link, the logs link and the submission URL.
  [@test] ../crates/avl-guest/src/lane/services/feedback/tests.rs

The control routes of the capture are these:

| route | method | request body | answer |
| --- | --- | --- | --- |
| `submissions` | GET | none | `{"submissions": [text]}`, the oldest first |
| `submissions` | DELETE | none | the removed submissions, in the same shape |
| `next` | POST | `{"waitMs": n}` | `{"submission": text}`, or `null` at the deadline |

- `next` takes the oldest submission. When the queue is empty, it waits at most `waitMs` for one.
  [@test] ../crates/avl-guest/src/lane/services/feedback/tests.rs

- A `waitMs` of more than 300000 gets 400 `bad_request`.
  [@test] ../crates/avl-guest/src/lane/services/feedback/tests.rs

- A stop of the service ends a waiting `next` with a `null` submission.
  [@test] ../crates/avl-guest/src/lane/services/feedback/tests.rs

### The Codex mock model

The model listens on the port 31500. It answers every turn with the reply `Air UI lane Codex reply`.

- A POST to `/v1/responses` answers a chunked `text/event-stream` with `Cache-Control: no-cache`.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

- The event stream of a turn is the golden `testdata/lane-services/codex-completed-reply.sse`.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

- The mock server of the backend tests answers the same golden event stream.
  [@test] ../../../../plugins/air/backend/cli/tests/codex/MockResponsesServerLaneGoldenTest.kt

- A title request gets the message `{"title":"Mock session"}` and is not a turn.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

- A GET of `/v1/models` lists the model `mock-model`. Another method gets 405.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

- The port 31500 is not the bridge port, not in the emulator range and below 32768.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

The control routes of the model are these:

| route | method | answer |
| --- | --- | --- |
| `mark` | GET | `{"mark": n}` |
| `turns?since=<mark>` | GET | `{"mark": n, "turns": [turn]}` |

A turn is `{"sequence", "model", "effort", "parsed"}`. The model and the effort come from `model` and
`reasoning.effort` of the request.

- A turn names the model and the effort of its request. A value that the request leaves out is `null`.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

- A request body that is no JSON object is a turn with `parsed` false.
  [@test] ../crates/avl-guest/src/lane/services/codex/tests.rs

## User Experience

The lane services have no user interface.

## Data & Backend

The tables of [Requirements](#requirements) give every route, every request body and every answer shape.

## Error Handling

Each refusal has a code: `lane_service_bind` and `lane_package_input` at the start, and `token_refused`,
`unknown_route`, `method_not_allowed`, `bad_request` and `unknown_fault` on the control route. A dial of the Agent
Spawner ends with a close code: 1000, 1001, 1002 or 1011.

## Testing / Local Run

Rewrite a golden with `UPDATE_EXPECT=1 cargo test -p avl-guest` in `community/tools/vm`. The tests of
`AirLaneServicesClientTest` read the golden of the control exchanges. Run them after a rewrite.

## Open Questions / Risks

None.

## References

- [ADR 0234](../../../../plugins/air/docs/decisions/0234-the-guest-agent-launches-the-lane-ide.md)
- [The UI-lane Controller](lane-controller.spec.md)
