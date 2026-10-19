"""
Accepts 2 or 3 args: project name, dir where one should be created and optional application name
"""
import sys, os
from django.core import management

project_name = sys.argv[1]
path = sys.argv[2]
if not os.path.exists(path):
    os.mkdir(path)

management.execute_from_command_line(argv=["django-admin", "startproject", project_name, path])

if len(sys.argv) > 3:
    app_name = sys.argv[3]
    app_path = os.path.join(path, app_name)
    # startapp requires an existing destination directory
    os.mkdir(app_path)
    management.execute_from_command_line(argv=["django-admin", "startapp", app_name, app_path])
