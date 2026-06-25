// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { AgentInfo } from "../model/types"
import { Select } from "./Select"

export function AgentSelector(props: {
  agents: AgentInfo[]
  selectedAgentId: string | null
  starting: boolean
  onSelect: (agentId: string) => void
}) {
  const placeholder = props.agents.length ? "Select an agent…" : "No agents in ~/.jetbrains/acp.json"
  return (
    <label className="acpAgentSelector">
      <span className="acpAgentSelectorLabel">Agent</span>
      <Select
        className="acpAgentSelect"
        value={props.selectedAgentId ?? ""}
        disabled={props.starting || props.agents.length === 0}
        placeholder={placeholder}
        options={props.agents.map(agent => ({ value: agent.id, label: agent.name }))}
        onValueChange={agentId => {
          if (agentId) props.onSelect(agentId)
        }}
      />
      {props.starting && <span className="acpAgentStarting">Starting…</span>}
    </label>
  )
}
