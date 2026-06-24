// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { PlanEntryView, PlanStatus } from "../model/types"

export function PlanView({ plan }: { plan: PlanEntryView[] }) {
  if (plan.length === 0) return null
  return (
    <section className="acpPlan" aria-label="Agent plan">
      <div className="acpPlanTitle">Plan</div>
      <ul className="acpPlanList">
        {plan.map((entry, index) => (
          <li key={index} className={`acpPlanItem acpPlanItem--${entry.status}`}>
            <span className="acpPlanMark" aria-hidden="true">{planMark(entry.status)}</span>
            <span className="acpPlanContent">{entry.content}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function planMark(status: PlanStatus): string {
  switch (status) {
    case "completed": return "✓"
    case "in_progress": return "▸"
    default: return "○"
  }
}
