// The Vite plugin that runs the harvest of a site: once before a production build, and in the dev server once at the
// start and again after each change of a harvest input, with a full reload after it.
//
// The kernel has no Vite dependency, so this file declares the small part of the Vite plugin shape that it uses. A
// site puts the result in the `plugins` of its `vite.config.ts`.

import { execFileSync } from 'node:child_process'

export interface HarvestPluginOptions {
  /** The plugin name, for example `air-architecture-harvest`. */
  name: string
  /** The prefix of the log lines of the dev server, for example `air-docs`. */
  label: string
  /** The absolute path of the harvest script of the site. */
  script: string
  /** The working directory of the harvest: the package directory of the site. */
  cwd: string
  /** The absolute globs that the dev server watches in addition to its own files. */
  watch: readonly string[]
  /** The name of a changed file in the log, or null when the change does not start a harvest. */
  trigger(path: string): string | null
}

/** The part of the Vite dev server that the plugin uses. */
interface HarvestServer {
  config: { logger: { info(message: string): void, error(message: string): void } }
  ws: { send(payload: { type: 'full-reload' }): void }
  watcher: {
    add(paths: string | readonly string[]): unknown
    on(event: 'add' | 'change' | 'unlink', listener: (path: string) => void): unknown
  }
}

/** The part of the Vite plugin shape that the harvest needs. */
export interface HarvestPlugin {
  name: string
  configResolved(config: { command: 'build' | 'serve' }): void
  buildStart(): void
  configureServer(server: HarvestServer): void
}

/** The plugin that runs `options.script` on the node of the build. */
export function harvestPlugin(options: HarvestPluginOptions): HarvestPlugin {
  const runHarvest = (): void => {
    execFileSync(process.execPath, [options.script], { cwd: options.cwd, stdio: 'inherit' })
  }
  let command: 'build' | 'serve' = 'serve'
  return {
    name: options.name,
    configResolved(config) {
      command = config.command
    },
    buildStart() {
      if (command === 'build') {
        runHarvest()
      }
    },
    configureServer(server) {
      let harvestTimer: ReturnType<typeof setTimeout> | undefined
      let pendingReason = 'startup'

      function scheduleHarvest(reason: string): void {
        pendingReason = reason
        if (harvestTimer != null) {
          clearTimeout(harvestTimer)
        }
        harvestTimer = setTimeout(() => {
          harvestTimer = undefined
          server.config.logger.info(`[${options.label}] refreshing harvested data (${pendingReason})`)
          try {
            runHarvest()
            server.ws.send({ type: 'full-reload' })
          }
          catch (error) {
            server.config.logger.error(`[${options.label}] harvest failed (${pendingReason})`)
            if (error instanceof Error && error.stack != null) {
              server.config.logger.error(error.stack)
            }
          }
        }, 100)
      }

      server.watcher.add(options.watch)
      for (const eventName of ['add', 'change', 'unlink'] as const) {
        server.watcher.on(eventName, (path) => {
          const name = options.trigger(path)
          if (name !== null) {
            scheduleHarvest(`${eventName} ${name}`)
          }
        })
      }

      runHarvest()
    },
  }
}
