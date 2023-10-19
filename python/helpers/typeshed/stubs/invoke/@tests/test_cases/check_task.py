from __future__ import annotations

from invoke import Context, task

# ===========================================
# This snippet is a regression test for #8936
# ===========================================


@task
def docker_build(context: Context) -> None:
    pass


@task(docker_build)
def docker_push(context: Context) -> None:
    pass
