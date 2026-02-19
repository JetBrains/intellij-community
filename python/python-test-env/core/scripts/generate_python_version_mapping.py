#!/usr/bin/env python3
# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

"""
Generator script for Python version to platform mapping.

This script fetches releases from:
- https://github.com/astral-sh/python-build-standalone (Python 3.x)
- https://github.com/qnox/python-2.7 (Python 2.7)

For each Python version, it generates a JSON file with:
- baseUrl: Base URL for downloads
- platforms: Available platforms (darwin, linux, windows)
- architectures: Available architectures (x86_64, aarch64, i686)
- libc: Available libc variants (gnu, musl)
- variants: Available variants (install_only, install_only_stripped, etc.)
- sha256: SHA256 checksum for each file

The output is written to python-version-mapping.json file.
"""

import hashlib
import json
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set
from urllib.request import Request, urlopen
from urllib.error import HTTPError


class GitHubReleasesFetcher:
    """Fetches and processes GitHub releases."""

    def __init__(self, github_token: Optional[str] = None):
        """
        Initialize the fetcher.

        Args:
            github_token: Optional GitHub personal access token to avoid rate limiting
        """
        self.github_token = github_token
        self.headers = {
            'Accept': 'application/vnd.github+json',
            'User-Agent': 'Python-Version-Mapping-Generator'
        }
        if github_token:
            self.headers['Authorization'] = f'Bearer {github_token}'

    def fetch_sha256(self, url: str) -> str:
        """
        Fetch SHA256 checksum from a .sha256 file.

        Args:
            url: URL to the .sha256 file

        Returns:
            SHA256 hash string

        Raises:
            Exception if the file cannot be fetched or parsed
        """
        request = Request(url, headers=self.headers)
        with urlopen(request, timeout=30) as response:
            content = response.read().decode('utf-8').strip()
            # Format is usually: "hash  filename" or just "hash"
            return content.split()[0]

    def fetch_releases(self, repo: str, max_pages: int = 10) -> List[Dict]:
        """
        Fetch releases from a GitHub repository.

        Args:
            repo: Repository in format "owner/repo"
            max_pages: Maximum number of pages to fetch

        Returns:
            List of release objects
        """
        all_releases = []
        for page in range(1, max_pages + 1):
            url = f'https://api.github.com/repos/{repo}/releases?per_page=10&page={page}'
            print(f'Fetching {url}...', file=sys.stderr)

            try:
                request = Request(url, headers=self.headers)
                with urlopen(request, timeout=60) as response:
                    releases = json.loads(response.read().decode('utf-8'))
                    if not releases:
                        break
                    all_releases.extend(releases)
                    print(f'Fetched {len(releases)} releases from page {page}', file=sys.stderr)
            except HTTPError as e:
                if e.code == 403:
                    print('Rate limit exceeded. Consider using a GitHub token.', file=sys.stderr)
                raise

        return all_releases


class AssetInfo:
    """Information about a Python build asset."""

    def __init__(self, asset_name: str, download_url: str, size: int, sha256: Optional[str],
                 version: str, release_tag: str, arch: str, vendor: str, os: str, abi: str, variant: str):
        """Private constructor. Use create() factory method instead."""
        self.asset_name = asset_name
        self.download_url = download_url
        self.size = size
        self.sha256 = sha256
        self.version = version
        self.release_tag = release_tag
        self.arch = arch
        self.vendor = vendor
        self.os = os
        self.abi = abi
        self.variant = variant

    @classmethod
    def from_asset(cls, asset_name: str, download_url: str, size: int, digest: Optional[str] = None) -> Optional['AssetInfo']:
        """
        Factory method to create an AssetInfo instance.

        Returns:
            AssetInfo instance if parsing succeeds, None otherwise
        """
        # Validate size
        if size <= 0:
            print(f'Skipping {asset_name}: Invalid size {size}', file=sys.stderr)
            return None

        # Extract SHA256 from digest format: "sha256:hash"
        sha256 = digest.split(':', 1)[1] if digest and digest.startswith('sha256:') else None

        # Parse asset name using split
        # Expected formats:
        # cpython-3.12.12+20251209-x86_64-unknown-linux-gnu-install_only.tar.gz
        # cpython-3.10.19+20251209-aarch64-apple-darwin-install_only.tar.gz

        # Remove extension first
        name_without_ext = asset_name
        if name_without_ext.endswith('.tar.gz'):
            name_without_ext = name_without_ext[:-7]
        elif name_without_ext.endswith('.zip'):
            name_without_ext = name_without_ext[:-4]
        else:
            print(f'Skipping {asset_name}: Unsupported file extension', file=sys.stderr)
            return None

        # Split by '-' delimiter
        parts = name_without_ext.split('-')

        if len(parts) < 6:
            print(f'Skipping {asset_name}: Not enough parts to parse', file=sys.stderr)
            return None

        # First part should be 'cpython'
        if parts[0] != 'cpython':
            print(f'Skipping {asset_name}: Does not start with cpython', file=sys.stderr)
            return None

        # Second part is version+release_tag (e.g., "3.12.12+20251209")
        version_tag = parts[1]
        if '+' not in version_tag:
            print(f'Skipping {asset_name}: Cannot parse version+release_tag', file=sys.stderr)
            return None
        version, release_tag = version_tag.split('+', 1)

        # Next parts: arch, vendor, os
        arch = parts[2]
        vendor = parts[3]
        os = parts[4]

        # Remaining parts determine if libc/abi is present
        # If we have exactly 6 parts: cpython, version+tag, arch, vendor, os, variant (no libc)
        # If we have 7+ parts: cpython, version+tag, arch, vendor, os, abi, variant...
        if len(parts) == 6:
            # No libc/abi (macOS case)
            abi = 'unknown'
            variant = parts[5]
        else:
            # Has libc/abi
            abi = parts[5]
            # Variant is everything after abi (joined back with '-')
            variant = '-'.join(parts[6:])

        return cls(asset_name, download_url, size, sha256, version, release_tag, arch, vendor, os, abi, variant)

    @property
    def platform(self) -> str:
        """Normalize platform name."""
        return self.os

    @property
    def libc(self) -> str:
        """Normalize libc name."""
        if self.abi in ('gnu', 'musl'):
            return self.abi
        elif self.abi == 'msvc':
            return 'msvc'
        else:
            return 'unknown'


class PythonVersionMapper:
    """Maps Python versions to available build options."""

    def __init__(self, fetcher: GitHubReleasesFetcher):
        self.fetcher = fetcher
        # version -> release_tag -> list of AssetInfo
        self.version_map: Dict[str, Dict[str, List[AssetInfo]]] = {}

    def should_include_asset(self, asset_info: AssetInfo) -> bool:
        """
        Check if an asset should be included in the mapping.

        Args:
            asset_info: Asset information to check

        Returns:
            True if the asset should be included, False otherwise
        """
        # Exclude musl libc variants
        if asset_info.libc == 'musl':
            return False

        # Only include x86_64 and aarch64 (arm64) architectures
        if asset_info.arch not in ('x86_64', 'aarch64', 'arm64'):
            return False

        return True

    def process_releases(self, repo: str, max_pages: int = 50) -> None:
        """Process releases from a repository."""
        print(f'Processing {repo} releases...', file=sys.stderr)
        releases = self.fetcher.fetch_releases(repo, max_pages)

        for release in releases:
            assets = release.get('assets', [])

            for asset in assets:
                asset_name = asset.get('name', '')

                # Skip non-archive files (only process .tar.gz and .zip)
                if not (asset_name.endswith('.tar.gz') or asset_name.endswith('.zip')):
                    continue

                size = asset.get('size', 0)
                digest = asset.get('digest')
                download_url = asset.get('browser_download_url')

                # Don't fetch SHA256 yet - will fetch only for latest releases in generate_mapping()
                asset_info = AssetInfo.from_asset(asset_name, download_url, size, digest)

                # Skip if asset parsing failed
                if asset_info is None:
                    continue

                # Check if asset should be included
                if not self.should_include_asset(asset_info):
                    continue

                # Add to version map
                if asset_info.version not in self.version_map:
                    self.version_map[asset_info.version] = {}

                if asset_info.release_tag not in self.version_map[asset_info.version]:
                    self.version_map[asset_info.version][asset_info.release_tag] = []

                self.version_map[asset_info.version][asset_info.release_tag].append(asset_info)

    def generate_mapping(self) -> Dict:
        """
        Generate the complete version mapping in JSON format.

        Returns:
            Dictionary with version -> build options mapping
        """
        result = {}

        for version, release_tags in sorted(self.version_map.items()):
            # Use the latest tag as primary
            latest_tag = max(release_tags.keys())

            # Only collect files from the latest release tag
            assets = release_tags[latest_tag]
            if not assets:
                continue

            files = {}
            for asset in assets:
                # Validate size
                if asset.size <= 0:
                    raise ValueError(f"Invalid size {asset.size} for {asset.asset_name}")

                # Fetch SHA256 if not present (only for latest releases)
                if not asset.sha256:
                    sha256_url = f"{asset.download_url}.sha256"
                    sha256_hash = self.fetcher.fetch_sha256(sha256_url)
                    asset.sha256 = sha256_hash
                    print(f'Fetched SHA256 for {asset.asset_name}', file=sys.stderr)

                # Create key from platform/arch/libc/variant combination
                key = f"{asset.platform}-{asset.arch}-{asset.libc}-{asset.variant}"
                # Extract just the filename from the URL
                filename = asset.download_url.rsplit('/', 1)[1]
                # Store: key -> dict with filename, sha256, and size
                files[key] = {
                    'filename': filename,
                    'sha256': asset.sha256,
                    'size': asset.size
                }

            if not files:
                continue

            # Build base URL with release tag
            # Example: https://cache-redirector.jetbrains.com/github.com/astral-sh/python-build-standalone/releases/download/20241016
            # Get any file's full URL to extract the base pattern
            sample_asset = assets[0]
            sample_url = sample_asset.download_url
            # Extract base: https://.../releases/download/TAG
            base_url_with_tag = sample_url.rsplit('/', 1)[0]

            # Use cache redirector for astral-sh/python-build-standalone
            if 'astral-sh/python-build-standalone' in base_url_with_tag:
                base_url_with_tag = base_url_with_tag.replace('https://github.com/', 'https://cache-redirector.jetbrains.com/github.com/')

            result[version] = {
                'baseUrl': base_url_with_tag,
                'files': files
            }

        return result


def write_json_file(version_map: Dict, output_path: Path) -> None:
    """
    Write the version mapping to a JSON file.

    Args:
        version_map: Dictionary mapping Python versions to build options
        output_path: Path to the output JSON file
    """
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(version_map, f, indent=2, sort_keys=True)

    print(f'Written {len(version_map)} version mappings to {output_path}', file=sys.stderr)


def main():
    """Main entry point."""
    import os
    import argparse

    parser = argparse.ArgumentParser(
        description='Generate Python version to platform mapping'
    )
    parser.add_argument(
        '--output',
        type=Path,
        default=Path(__file__).parent.parent / 'gen' / 'com' / 'intellij' / 'python' / 'test' / 'env' / 'core' / 'python-version-mapping.json',
        help='Output JSON file path'
    )
    parser.add_argument(
        '--token',
        type=str,
        default=os.environ.get('GITHUB_TOKEN'),
        help='GitHub personal access token (or set GITHUB_TOKEN env var)'
    )

    args = parser.parse_args()

    # Create fetcher and mapper
    fetcher = GitHubReleasesFetcher(github_token=args.token)
    mapper = PythonVersionMapper(fetcher)

    # Process both repositories
    # Note: python-build-standalone has 1000+ releases, need many pages to get all platforms
    mapper.process_releases('astral-sh/python-build-standalone', max_pages=200)
    mapper.process_releases('qnox/python-2.7', max_pages=10)

    # Generate mapping
    version_map = mapper.generate_mapping()

    if not version_map:
        print('Error: No version mappings generated', file=sys.stderr)
        sys.exit(1)

    # Write output
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json_file(version_map, args.output)

    print(f'Successfully generated mapping with {len(version_map)} versions', file=sys.stderr)


if __name__ == '__main__':
    main()
