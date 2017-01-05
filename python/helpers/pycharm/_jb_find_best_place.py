# First arg is project name. It searches for best place for project
# Result is current_dir \n project_name. So, best place is current_dir/project_name
import os, sys

project_name = sys.argv[1]
current_folder = os.getcwd()
print(current_folder)
while True:
    project_folder = os.path.join(current_folder, project_name)
    if not os.path.exists(project_name):
        print(project_name)
        break
    else:
        project_name += "_"
