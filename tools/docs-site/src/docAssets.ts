// The hashed URLs of the pictures that the harvest copies (the `assets` of the site data, docAssets.ts of the kernel
// scripts). Each picture is its own hashed file.

import { site } from './site.ts'

/** The URL of a harvested picture by its content-relative path, or null when the harvest did not copy it. */
export function docAssetUrl(path: string): string | null {
  return site().index.data.assets[path] ?? null
}
