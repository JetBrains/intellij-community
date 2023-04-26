#  Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from _pydevd_bundle.pydevd_constants import IS_ASYNCIO_DEBUGGER_ENV, IS_ASYNCIO_REPL
from _pydevd_bundle.pydevd_exec2 import Exec
from _pydev_bundle.pydev_log import warn


eval_async_expression_in_context = None
eval_async_expression = None
exec_async_code = None
asyncio_command_compiler = None

if IS_ASYNCIO_DEBUGGER_ENV or IS_ASYNCIO_REPL:
    from _pydevd_bundle import pydevd_save_locals
    from _pydevd_asyncio_util.pydevd_nest_asyncio import apply, PyDevCoro
    from codeop import CommandCompiler
    import ast, types, inspect, asyncio

    FILENAME = '<string>'
    EVAL_SYMBOL = 'eval'
    EXEC_SYMBOL = 'exec'
    MODULE = '<module>'

    _asyncio_command_compiler = CommandCompiler()
    _asyncio_command_compiler.compiler.flags |= ast.PyCF_ALLOW_TOP_LEVEL_AWAIT


    def _compile_async_expression(expression, do_exec):
        """
        Compile an expression with 'eval' or 'exec' compilation flag.\n
        Can compile an expression with `await` outside function.

        :param str expression: compilation target
        :param bool do_exec: if True then compilation flag is 'exec' else 'eval'
        :return: a pair of compilation result and compilation flag
        :raises (OverflowError, SyntaxError, ValueError): if compilation failed
        """
        compilation_flag = EVAL_SYMBOL
        if do_exec:
            try:
                compiled = asyncio_command_compiler(expression, FILENAME, compilation_flag)
            except (OverflowError, SyntaxError, ValueError):
                compilation_flag = EXEC_SYMBOL
                compiled = asyncio_command_compiler(expression, FILENAME, compilation_flag)
        else:
            compiled = asyncio_command_compiler(expression, FILENAME, compilation_flag)
        return compiled, compilation_flag


    def _eval_async_expression_in_context(expression, global_names, local_names, do_exec):
        """
        Compile an expression and if the compilation result is coroutine then put it in asyncio event loop else evaluate.\n
        Can evaluate an expression with `await` outside function.

        :param str expression: evaluation target
        :param global_names: the dictionary implementing the current module namespace
        :param local_names: the dictionary representing the current local symbol table
        :param bool do_exec: if True then the compilation flag is 'exec' else 'eval'
        :return: evaluation result
        :raises (OverflowError, SyntaxError, ValueError): if a compilation failed
        """
        apply()
        updated_globals = {}
        updated_globals.update(global_names)
        updated_globals.update(local_names)

        compiled, _ = _compile_async_expression(expression, do_exec)
        return exec_async_code(compiled, updated_globals)


    def _eval_async_expression(expression, global_names, frame, do_exec, exception_handler):
        """
        Compile an expression and if the compilation result is coroutine then put it in asyncio event loop else evaluate.\n
        Can evaluate an expression with `await` outside function.

        :param str expression: evaluation target
        :param global_names: the dictionary implementing the current module namespace and the current local symbol table
        :param frame: the current frame
        :param bool do_exec: if True then the compilation flag is 'exec' else 'eval'
        :param exception_handler: handle an exception thrown at compile time
        :return: evaluation result or exception string
        """
        apply()
        locals = frame.f_locals
        try:
            compiled, compilation_flag = _compile_async_expression(expression, do_exec)
            if compiled is None:
                try:
                    compile(expression, FILENAME, compilation_flag, asyncio_command_compiler.compiler.flags)
                except (OverflowError, SyntaxError, ValueError):
                    return exception_handler(expression, locals)
            result = exec_async_code(compiled, global_names)
            if compilation_flag == EXEC_SYMBOL:
                Exec(expression, global_names, frame.f_locals)
                pydevd_save_locals.save_locals(frame)
            return result
        except (OverflowError, SyntaxError, ValueError):
            return exception_handler(expression, locals)


    def _exec_async_code(code, global_names):
        """
        If code is coroutine then put it in an asyncio event loop else evaluate

        :param code: evaluation target
        :param global_names: the dictionary implementing the current module namespace
        :return: evaluation result
        """
        try:
            apply()
        except:
            warn('Failed to patch asyncio')
        func = types.FunctionType(code, global_names)
        result = func()
        try:
            if inspect.iscoroutine(result) and MODULE in str(result):
                loop = asyncio.get_event_loop()
                result = loop.run_until_complete(PyDevCoro(result))
        except:
            warn('Failed to run coroutine %s' % str(result))
        finally:
            return result


    eval_async_expression_in_context = _eval_async_expression_in_context
    eval_async_expression = _eval_async_expression
    exec_async_code = _exec_async_code
    asyncio_command_compiler = _asyncio_command_compiler
