@ECHO OFF

SET pluginVersion=0.18

for %%X in (2017.2 2017.2.6 2017.3 2017.3.1 2017.3.2 2017.3.3 2017.3.4) do call :buildPlugin %%X

:buildPlugin
SETLOCAL
echo Called with %1
SET IDEA_VERSION=%1
call gradlew clean
call gradlew buildPlugin check
call gradlew publishPlugin
copy build\distributions\lombok-plugin-%pluginVersion%.zip distro\lombok-plugin-%pluginVersion%-%1.zip
ENDLOCAL & SET result=%retval%
