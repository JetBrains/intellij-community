// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// noinspection JSUnresolvedVariable,JSUnresolvedFunction,JSValidateTypes
(function () {
  /**
   * @typedef {Object} Task
   * @property {string} id
   * @property {string} title
   * @property {string} owner
   * @property {string} status
   * @property {string} priority
   * @property {boolean} blocked
   * @property {number} progress
   * @property {number} dueOffset
   * @property {string} dueLabel
   * @property {number} estimateHours
   * @property {string[]} tags
   */

  /**
   * @typedef {Object} TimelineEvent
   * @property {string} id
   * @property {string} taskId
   * @property {string} label
   */

  const rootNode = document.getElementById("root");
  if (!rootNode) {
    return;
  }

  function renderFallback(reason) {
    rootNode.innerHTML = "";
    const wrapper = document.createElement("div");
    wrapper.className = "fallback";

    const title = document.createElement("h2");
    title.textContent = "React runtime is unavailable";
    wrapper.appendChild(title);

    const info = document.createElement("p");
    info.textContent = reason;
    wrapper.appendChild(info);

    const list = document.createElement("div");
    list.className = "fallback-list";
    for (let i = 1; i <= 120; i++) {
      const item = document.createElement("div");
      item.className = "fallback-list-item";
      item.textContent = "Fallback row " + i + " (static mode)";
      list.appendChild(item);
    }
    wrapper.appendChild(list);
    rootNode.appendChild(wrapper);
  }

  const reactGlobal = window["React"];
  const reactDomGlobal = window["ReactDOM"];
  if (!reactGlobal || !reactDomGlobal) {
    renderFallback("Could not load React/ReactDOM scripts from CDN.");
    return;
  }

  const ReactLib = reactGlobal;
  const ReactDomLib = reactDomGlobal;
  const h = ReactLib.createElement;
  const useEffect = ReactLib.useEffect;
  const useMemo = ReactLib.useMemo;
  const useState = ReactLib.useState;

  const STATUSES = ["Backlog", "In Progress", "Review", "Done"];
  const PRIORITIES = ["Low", "Medium", "High", "Critical"];
  const OWNERS = ["Alice", "Bob", "Carol", "Diana", "Evan", "Fatima", "George", "Helen"];
  const DOMAINS = ["WebView", "Editor", "Terminal", "Search", "Navigation", "Inspections"];

  const PRIORITY_WEIGHT = {
    Low: 1,
    Medium: 2,
    High: 3,
    Critical: 4,
  };

  function cssStatus(status) {
    return "status-" + status.toLowerCase().replace(/\s+/g, "-");
  }

  function cssPriority(priority) {
    return "priority-" + priority.toLowerCase();
  }

  function dayLabel(offsetDays) {
    const date = new Date();
    date.setDate(date.getDate() + offsetDays);
    return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  }

  /** @type {Task[]} */
  const TASKS = Array.from({ length: 168 }, function (_, index) {
    const status = STATUSES[index % STATUSES.length];
    const priority = PRIORITIES[(index * 7) % PRIORITIES.length];
    const blocked = index % 11 === 0;
    const owner = OWNERS[index % OWNERS.length];
    const domain = DOMAINS[index % DOMAINS.length];
    const progressBase = status === "Done" ? 100 : status === "Review" ? 76 : status === "In Progress" ? 45 : 14;
    const progress = Math.min(100, progressBase + (index % 18));
    const dueOffset = (index % 15) - 4;

    return {
      id: "WVW-" + String(1000 + index),
      title: domain + " rich interaction scenario #" + (index + 1),
      owner: owner,
      status: status,
      priority: priority,
      blocked: blocked,
      progress: progress,
      dueOffset: dueOffset,
      dueLabel: dayLabel(dueOffset),
      estimateHours: 2 + (index % 8),
      tags: [domain, index % 2 === 0 ? "UI" : "Bridge", index % 3 === 0 ? "Regression" : "Smoke"],
    };
  });

  /** @type {TimelineEvent[]} */
  const TIMELINE = Array.from({ length: 320 }, function (_, index) {
    const task = TASKS[index % TASKS.length];
    const action = ["status changed", "comment added", "review requested", "build linked", "QA note"][index % 5];
    const minutesAgo = 3 + index * 2;
    return {
      id: "EVT-" + String(index + 1),
      taskId: task.id,
      label: task.id + " • " + action + " • " + minutesAgo + "m ago",
    };
  });

  /**
   * @param {{accent: string, label: string, value: string, note: string}} props
   */
  function MetricCard(props) {
    return h(
      "div",
      { className: "metric-card", "data-accent": props.accent },
      h("div", { className: "metric-label" }, props.label),
      h("div", { className: "metric-value" }, props.value),
      h("div", { className: "metric-note" }, props.note)
    );
  }

  /**
   * @param {{task: Task, active: boolean, onSelect: function(string): void}} props
   */
  function TaskCard(props) {
    const task = props.task;
    return h(
      "article",
      {
        className: props.active ? "task-card is-active" : "task-card",
        onClick: function () {
          props.onSelect(task.id);
        },
      },
      h("div", { className: "task-title" }, task.title),
      h("div", { className: "task-subtitle" }, task.id + " • " + task.owner),
      h(
        "div",
        { className: "badge-row" },
        h("span", { className: "badge " + cssStatus(task.status) }, task.status),
        h("span", { className: "badge " + cssPriority(task.priority) }, task.priority),
        task.blocked ? h("span", { className: "badge priority-critical" }, "Blocked") : null
      ),
      h(
        "div",
        { className: "progress" },
        h("div", {
          className: "progress-fill",
          style: { width: String(task.progress) + "%" },
        })
      )
    );
  }

  function App() {
    const [query, setQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("All");
    const [priorityFilter, setPriorityFilter] = useState("All");
    const [blockedOnly, setBlockedOnly] = useState(false);
    const [selectedTaskId, setSelectedTaskId] = useState(TASKS[0].id);
    const [clock, setClock] = useState(new Date());

    useEffect(function () {
      const timer = window.setInterval(function () {
        setClock(new Date());
      }, 1000);
      return function () {
        window.clearInterval(timer);
      };
    }, []);

    /** @type {Task[]} */
    const filteredTasks = useMemo(
      function () {
        const normalizedQuery = query.trim().toLowerCase();
        return TASKS.filter(function (task) {
          if (statusFilter !== "All" && task.status !== statusFilter) {
            return false;
          }
          if (priorityFilter !== "All" && task.priority !== priorityFilter) {
            return false;
          }
          if (blockedOnly && !task.blocked) {
            return false;
          }
          if (!normalizedQuery) {
            return true;
          }
          return (
            task.id.toLowerCase().includes(normalizedQuery) ||
            task.title.toLowerCase().includes(normalizedQuery) ||
            task.owner.toLowerCase().includes(normalizedQuery)
          );
        }).sort(function (a, b) {
          if (PRIORITY_WEIGHT[b.priority] !== PRIORITY_WEIGHT[a.priority]) {
            return PRIORITY_WEIGHT[b.priority] - PRIORITY_WEIGHT[a.priority];
          }
          return a.id.localeCompare(b.id);
        });
      },
      [query, statusFilter, priorityFilter, blockedOnly]
    );

    const grouped = useMemo(
      function () {
        const map = {};
        STATUSES.forEach(function (status) {
          map[status] = [];
        });
        filteredTasks.forEach(function (task) {
          map[task.status].push(task);
        });
        return map;
      },
      [filteredTasks]
    );

    /** @type {Task | null} */
    const selectedTask = useMemo(
      function () {
        if (filteredTasks.length === 0) {
          return null;
        }
        for (let i = 0; i < filteredTasks.length; i++) {
          if (filteredTasks[i].id === selectedTaskId) {
            return filteredTasks[i];
          }
        }
        return filteredTasks[0];
      },
      [filteredTasks, selectedTaskId]
    );

    useEffect(
      function () {
        if (selectedTask && selectedTask.id !== selectedTaskId) {
          setSelectedTaskId(selectedTask.id);
        }
      },
      [selectedTask, selectedTaskId]
    );

    const doneCount = filteredTasks.filter(function (task) {
      return task.status === "Done";
    }).length;
    const blockedCount = filteredTasks.filter(function (task) {
      return task.blocked;
    }).length;
    const avgProgress =
      filteredTasks.length > 0
        ? Math.round(
            filteredTasks.reduce(function (sum, task) {
              return sum + task.progress;
            }, 0) / filteredTasks.length
          )
        : 0;
    const urgentCount = filteredTasks.filter(function (task) {
      return task.priority === "Critical" || task.priority === "High";
    }).length;

    /** @type {TimelineEvent[]} */
    const timelineItems = useMemo(
      function () {
        const selectedId = selectedTask ? selectedTask.id : null;
        return TIMELINE.filter(function (event, index) {
          return selectedId == null || event.taskId === selectedId || index % 7 === 0;
        }).slice(0, 180);
      },
      [selectedTask]
    );

    return h(
      "div",
      { className: "app-shell" },
      h(
        "section",
        { className: "panel header" },
        h(
          "div",
          { className: "title-block" },
          h("h1", null, "WebView React Rich UI Sample"),
          h(
            "p",
            null,
            "System WebView + React smoke scenario: filterable board, details panel, timeline and long scroll feed."
          )
        ),
        h(
          "div",
          { className: "header-meta" },
          h(
            "div",
            { className: "filters" },
            h(
              "div",
              { className: "filter-row" },
              h("input", {
                className: "search-input",
                value: query,
                placeholder: "Search by id, title or owner",
                onChange: function (event) {
                  setQuery(event.target.value);
                },
              }),
              h(
                "select",
                {
                  className: "select-control",
                  value: statusFilter,
                  onChange: function (event) {
                    setStatusFilter(event.target.value);
                  },
                },
                h("option", { value: "All" }, "All statuses"),
                STATUSES.map(function (status) {
                  return h("option", { key: status, value: status }, status);
                })
              ),
              h(
                "select",
                {
                  className: "select-control",
                  value: priorityFilter,
                  onChange: function (event) {
                    setPriorityFilter(event.target.value);
                  },
                },
                h("option", { value: "All" }, "All priorities"),
                PRIORITIES.map(function (priority) {
                  return h("option", { key: priority, value: priority }, priority);
                })
              )
            ),
            h(
              "div",
              { className: "filter-row" },
              h(
                "button",
                {
                  className: blockedOnly ? "btn-control is-active" : "btn-control",
                  type: "button",
                  onClick: function () {
                    setBlockedOnly(!blockedOnly);
                  },
                },
                "Blocked only"
              ),
              h(
                "button",
                {
                  className: "btn-control",
                  type: "button",
                  onClick: function () {
                    setQuery("");
                    setStatusFilter("All");
                    setPriorityFilter("All");
                    setBlockedOnly(false);
                  },
                },
                "Reset filters"
              ),
              h("span", { className: "clock" }, "Now: " + clock.toLocaleTimeString())
            )
          )
        )
      ),
      h(
        "section",
        { className: "metrics-grid" },
        h(MetricCard, {
          accent: "a",
          label: "Visible Tasks",
          value: String(filteredTasks.length),
          note: "Filtered from " + String(TASKS.length),
        }),
        h(MetricCard, {
          accent: "b",
          label: "Completion",
          value: String(doneCount) + " done",
          note: filteredTasks.length === 0 ? "0%" : String(Math.round((doneCount / filteredTasks.length) * 100)) + "% complete",
        }),
        h(MetricCard, {
          accent: "c",
          label: "Average Progress",
          value: String(avgProgress) + "%",
          note: "Across active cards",
        }),
        h(MetricCard, {
          accent: "d",
          label: "Urgent / Blocked",
          value: String(urgentCount) + " / " + String(blockedCount),
          note: "High+Critical / blocked",
        })
      ),
      h(
        "section",
        { className: "main-grid" },
        h(
          "div",
          { style: { display: "flex", flexDirection: "column", gap: "12px" } },
          h(
            "div",
            { className: "panel board-panel" },
            h("h2", { className: "panel-title" }, "Kanban Board"),
            h(
              "div",
              { className: "kanban-columns" },
              STATUSES.map(function (status) {
                const items = grouped[status] || [];
                return h(
                  "div",
                  { className: "kanban-column", key: status },
                  h("div", { className: "column-header" }, status + " (" + String(items.length) + ")"),
                  h(
                    "div",
                    { className: "task-list" },
                    items.slice(0, 20).map(function (task) {
                      return h(TaskCard, {
                        key: task.id,
                        task: task,
                        active: selectedTask != null && selectedTask.id === task.id,
                        onSelect: setSelectedTaskId,
                      });
                    })
                  )
                );
              })
            )
          ),
          h(
            "div",
            { className: "panel board-panel" },
            h("h2", { className: "panel-title" }, "Release Timeline (scroll)"),
            h(
              "div",
              { className: "timeline-list" },
              timelineItems.map(function (event) {
                return h("div", { className: "timeline-item", key: event.id }, event.label);
              })
            )
          )
        ),
        h(
          "div",
          { className: "side-stack" },
          h(
            "div",
            { className: "panel details-panel" },
            h("h2", { className: "panel-title" }, "Selected Task"),
            selectedTask == null
              ? h("div", { className: "details-value" }, "No tasks match current filters.")
              : h(
                  "div",
                  null,
                  h("div", { className: "task-title" }, selectedTask.title),
                  h("div", { className: "task-subtitle" }, selectedTask.id + " • due " + selectedTask.dueLabel),
                  h(
                    "div",
                    { className: "details-grid", style: { marginTop: "10px" } },
                    h("div", { className: "details-key" }, "Owner"),
                    h("div", { className: "details-value" }, selectedTask.owner),
                    h("div", { className: "details-key" }, "Status"),
                    h("div", { className: "details-value" }, selectedTask.status),
                    h("div", { className: "details-key" }, "Priority"),
                    h("div", { className: "details-value" }, selectedTask.priority),
                    h("div", { className: "details-key" }, "Estimate"),
                    h("div", { className: "details-value" }, String(selectedTask.estimateHours) + "h"),
                    h("div", { className: "details-key" }, "Blocked"),
                    h("div", { className: "details-value" }, selectedTask.blocked ? "Yes" : "No")
                  ),
                  h(
                    "div",
                    { className: "details-tags" },
                    selectedTask.tags.map(function (tag) {
                      return h("span", { className: "badge status-backlog", key: tag }, tag);
                    })
                  )
                )
          ),
          h(
            "div",
            { className: "panel activity-panel" },
            h("h2", { className: "panel-title" }, "Activity Feed"),
            h(
              "div",
              { className: "activity-list" },
              TIMELINE.slice(0, 220).map(function (event) {
                return h("div", { className: "activity-item", key: event.id }, event.label);
              })
            )
          )
        )
      ),
      h(
        "div",
        { className: "footer-row" },
        "Resources are loaded through the WebView asset handler. React app runs in a native system WebView."
      )
    );
  }

  try {
    const rootApi =
      typeof ReactDomLib.createRoot === "function"
        ? ReactDomLib.createRoot(rootNode)
        : {
            render: function (element) {
              ReactDomLib.render(element, rootNode);
            },
          };
    rootApi.render(h(App));
  }
  catch (error) {
    const message = error && error.message ? error.message : "Unknown React bootstrap error";
    renderFallback("React app initialization failed: " + message);
  }
})();
