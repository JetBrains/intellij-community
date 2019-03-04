// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.apache.maven.plugin;

/**
 * @goal
 * @requiresDependencyResolution
 * @requiresProject
 * @requiresReports
 * @aggregator
 * @requiresOnline
 * @requiresDirectInvocation
 * @phase
 * @execute
 * <warning descr="Wrong tag 'ololo'">@ololo</warning>
 */
class TestMojo implements Mojo {
}

interface Mojo {
}