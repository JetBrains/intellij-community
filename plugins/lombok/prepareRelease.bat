@ECHO OFF

SET pluginVersion=0.16

for %%X in (2017.2 2017.2.1 2017.2.2 2017.2.3 2017.2.4 2017.2.5 2017.2.6 2017.3 2017.3.1 2017.3.2 2017.3.3) do call :buildPlugin %%X

:buildPlugin
SETLOCAL
echo Called with %1
SET IDEA_VERSION=%1
call gradlew clean
call gradlew buildPlugin check
copy build\distributions\lombok-plugin-%pluginVersion%.zip distro\lombok-plugin-%pluginVersion%-%1.zip
ENDLOCAL & SET result=%retval%
