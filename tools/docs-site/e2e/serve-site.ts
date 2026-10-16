// Static server for the built site (`outDir` of the site config) used by the Playwright webServer config of a site
// and by the diagram shots: serves under the `base` of the site config with SPA fallback to index.html, mirroring the
// deployment layout. Run it as `node serve-site.ts SITE_CONFIG`; `SITE_PORT` picks the port.

import { existsSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import { extname, join, normalize, sep } from 'node:path'
import { loadSiteConfig, outDir } from '../scripts/paths.ts'

const config = await loadSiteConfig(process.argv[2])
const siteOut = outDir(config)
const mountPath = config.base.endsWith('/') ? config.base : `${config.base}/`
const port = Number(process.env.SITE_PORT ?? 4318)

function staticSitePath(path: string): string {
  if (path === mountPath.slice(0, -1) || !path.startsWith(mountPath)) {
    return 'index.html'
  }
  const relativePath = decodeURIComponent(path.slice(mountPath.length))
  return relativePath === '' ? 'index.html' : relativePath
}

function contentType(path: string): string {
  switch (extname(path)) {
    case '.css': return 'text/css; charset=utf-8'
    case '.html': return 'text/html; charset=utf-8'
    case '.js': return 'text/javascript; charset=utf-8'
    case '.json': return 'application/json; charset=utf-8'
    case '.svg': return 'image/svg+xml'
    default: return 'application/octet-stream'
  }
}

const server = createServer(async (request, response) => {
  const requestPath = new URL(request.url ?? '/', 'http://127.0.0.1').pathname
  const filePath = normalize(join(siteOut, staticSitePath(requestPath)))
  if (filePath !== siteOut && !filePath.startsWith(siteOut + sep)) {
    response.writeHead(403)
    response.end()
    return
  }
  const resolved = existsSync(filePath) ? filePath : join(siteOut, 'index.html')
  try {
    const body = await readFile(resolved)
    response.writeHead(200, { 'Content-Type': contentType(resolved) })
    response.end(body)
  }
  catch {
    response.writeHead(404)
    response.end()
  }
})

server.listen(port, '127.0.0.1', () => {
  console.log(`${config.id}-site served at http://127.0.0.1:${port}${mountPath}`)
})
