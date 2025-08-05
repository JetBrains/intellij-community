// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.notification.impl.NotificationIdsHolder
import org.jetbrains.annotations.ApiStatus

internal class ConfiguredPythonInterpreterIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    SDK_HAS_BEEN_CONFIGURED_AS_THE_PROJECT_INTERPRETER,
  )

  companion object {
    const val SDK_HAS_BEEN_CONFIGURED_AS_THE_PROJECT_INTERPRETER = "sdk.has.been.configured.as.the.project.interpreter"
  }
}

internal class PythonPackagesIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    PYTHON_PACKAGE_INSTALLED,
    PYTHON_PACKAGE_DELETED,
  )

  companion object {
    const val PYTHON_PACKAGE_INSTALLED = "python.package.installed"
    const val PYTHON_PACKAGE_DELETED = "python.package.deleted"
  }
}

@ApiStatus.Internal
class PythonDebuggerIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    CONNECTION_FAILED,
    JUPYTER_CELL_EDITED_BETWEEN_SESSIONS,
    JUPYTER_CELL_EDITED_DURING_SESSIONS,
    JUPYTER_OTHER_CELL_UNDER_DEBUGGER,
  )

  companion object {
    const val CONNECTION_FAILED: String = "connection.failed"
    const val JUPYTER_CELL_EDITED_BETWEEN_SESSIONS: String = "jupyter.cell.edited.between.sessions"
    const val JUPYTER_CELL_EDITED_DURING_SESSIONS: String = "jupyter.cell.edited.during.sessions"
    const val JUPYTER_OTHER_CELL_UNDER_DEBUGGER: String = "jupyter.other.cell.under.debugger"
  }
}


internal class PythonCompatibilityInspectionAdvertiserIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    STALE_PYTHON_VERSION,
    USING_FUTURE_IMPORTS,
    USING_SIX_PACKAGE,
  )

  companion object {
    const val STALE_PYTHON_VERSION = "stale.python.version"
    const val USING_FUTURE_IMPORTS = "using.future.imports"
    const val USING_SIX_PACKAGE = "using.six.package"
  }
}


internal class CythonWarningIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    CYTHON_EXTENSION_SPEEDS_UP_PYTHON_DEBUGGING,
  )

  companion object {
    const val CYTHON_EXTENSION_SPEEDS_UP_PYTHON_DEBUGGING = "cython.extension.speeds.up.python.debugging"
  }
}

internal class PackageRequirementsIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    REQUIREMENTS_HAVE_BEEN_IGNORED,
  )

  companion object {
    const val REQUIREMENTS_HAVE_BEEN_IGNORED = "requirements.have.been.ignored"
  }
}

internal class BlackFormatterIntegrationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    BLACK_FORMATTER_SUPPORT,
  )

  companion object {
    const val BLACK_FORMATTER_SUPPORT = "black.formatter.support"
  }
}

internal class PythonSDKUpdaterIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    REFRESH_SKELETONS_FOR_REMOTE_INTERPRETER_FAILED,
    REMOTE_INTERPRETER_SUPPORT_IS_NOT_AVAILABLE,
  )

  companion object {
    const val REFRESH_SKELETONS_FOR_REMOTE_INTERPRETER_FAILED = "refresh.skeletons.for.remote.interpreter.failed"
    const val REMOTE_INTERPRETER_SUPPORT_IS_NOT_AVAILABLE = "remote.interpreter.support.is.not.available"
  }
}

internal class SyncPythonRequirementsIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    NO_INTERPRETER_CONFIGURED,
    CREATE_REQUIREMENTS_FILE_FAILED,
    ANALYZE_ENTRIES_IN_REQUIREMENTS_FILE_FAILED,
    SOME_REQUIREMENTS_FROM_BASE_FILES_WERE_NOT_UPDATED,
  )

  companion object {
    const val NO_INTERPRETER_CONFIGURED = "no.interpreter.configured"
    const val CREATE_REQUIREMENTS_FILE_FAILED = "create.requirements.file.failed"
    const val ANALYZE_ENTRIES_IN_REQUIREMENTS_FILE_FAILED = "analyze.entries.in.requirements.file.failed"
    const val SOME_REQUIREMENTS_FROM_BASE_FILES_WERE_NOT_UPDATED = "some.requirements.from.base.files.were.not.updated"
  }
}

internal class PythonInterpreterInstallationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    PYTHON_INSTALLATION_INTERRUPTED,
  )

  companion object {
    const val PYTHON_INSTALLATION_INTERRUPTED = "python.installation.interrupted"
  }
}

internal class PipfileWatcherIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    RUN_PIPENV_LOCK_SUGGESTION,
  )

  companion object {
    const val RUN_PIPENV_LOCK_SUGGESTION = "run.pipenv.lock.suggestion"
  }
}
