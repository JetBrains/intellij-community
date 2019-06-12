# coding=utf-8
import os

__all__ = ['is_running_under_teamcity']

__version__ = "1.25"

teamcity_presence_env_var = "TEAMCITY_VERSION"


def is_running_under_teamcity():
    return bool(os.getenv(teamcity_presence_env_var))
