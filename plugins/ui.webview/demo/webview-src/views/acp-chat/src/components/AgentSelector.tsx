// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { AgentInfo } from "../model/types"
import { acpIconSrc, AGENT_ICON_PATH, JUNIE_ICON_PATH } from "./icons/AcpChatIconSet"
import { Select, SelectItem, SelectSeparator } from "./Select"

const OPEN_ACP_CONFIG_VALUE = "__open_acp_config__"

export function AgentSelector(props: {
  agents: AgentInfo[]
  selectedAgentId: string | null
  starting: boolean
  onSelect: (agentId: string) => void
  onOpenConfig: () => void
}) {
  const placeholder = props.agents.length ? "Select an agent…" : "No agents in ~/.jetbrains/acp.json"
  const selectedAgent = props.agents.find(agent => agent.id === props.selectedAgentId)
  const options = props.agents.map(agent => ({ value: agent.id, label: <AgentSelectItem agent={agent} />, textValue: agent.name }))
  return (
    <label className="acpAgentSelector">
      <span className="acpAgentSelectorIcon" title="Agent" aria-hidden="true">
        <jb-icon src={acpIconSrc(AGENT_ICON_PATH)} />
      </span>
      <Select
        className="acpAgentSelect"
        value={props.selectedAgentId ?? ""}
        disabled={props.starting}
        placeholder={placeholder}
        triggerAriaLabel={`Agent: ${selectedAgent?.name ?? placeholder}`}
        options={options}
        onValueChange={value => {
          if (value === OPEN_ACP_CONFIG_VALUE) {
            props.onOpenConfig()
          }
          else if (value) {
            props.onSelect(value)
          }
        }}
      >
        {props.agents.map((agent, index) => (
          <SelectItem key={`${agent.id}-${index}`} value={agent.id} textValue={agent.name}>
            <AgentSelectItem agent={agent} />
          </SelectItem>
        ))}
        {props.agents.length > 0 ? <SelectSeparator /> : null}
        <SelectItem value={OPEN_ACP_CONFIG_VALUE} textValue="Open acp.json">
          <span className="acpAgentSelectConfigItem">Open acp.json</span>
        </SelectItem>
      </Select>
      {props.starting && <span className="acpAgentStarting">Starting…</span>}
    </label>
  )
}

function AgentSelectItem(props: { agent: AgentInfo }) {
  return (
    <span className="acpAgentSelectItemContent">
      {props.agent.icon === "junie" ? (
        <span className="acpAgentSelectItemIcon" aria-hidden="true">
          <jb-icon src={acpIconSrc(JUNIE_ICON_PATH)} />
        </span>
      ) : null}
      <span className="acpAgentSelectItemName">{props.agent.name}</span>
    </span>
  )
}
