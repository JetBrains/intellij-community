/**
 * Monorepo test plan tests for pyproject.toml module synchronization.
 *
 * <p>Test data resources are taken from
 * <a href="https://jetbrains.team/p/pyqa/repositories/test-plan/files/master/monorepo">
 * pyqa/test-plan/monorepo</a> — a collection of real-world monorepo layouts covering
 * uv workspaces, Poetry path dependencies, Hatch workspaces, setuptools, Django, and
 * mixed-tool configurations.
 *
 * <p>Each test class points its {@code @TestDataPath} to a subdirectory under
 * {@code testData/monorepo/}. The {@code @PyDefaultTestApplication} framework copies
 * the test data into a temporary project directory, then {@code pyProjectTomlSyncFixture}
 * triggers a full pyproject.toml sync and {@code assertProjectStructure} verifies the
 * resulting module names, types, content roots, source roots, excluded folders, and
 * inter-module dependencies.
 */
package com.intellij.python.junit5Tests.unit.pyproject.model.testplan;
