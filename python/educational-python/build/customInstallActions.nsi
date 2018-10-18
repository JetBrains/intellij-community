!include "TextFunc.nsh"
!define INSTALL_OPTION_ELEMENTS 10
!define PYTHON_VERSIONS 4
!define CUSTOM_SILENT_CONFIG 1

Var internetConnection
${StrTok}


Function customPreInstallActions
  IfFileExists "$TEMP\python.txt" deletePythonFileInfo getPythonFileInfo
deletePythonFileInfo:
  Delete "$TEMP\python.txt"
getPythonFileInfo:
  inetc::get "https://www.jetbrains.com/updates/python.txt" "$TEMP\python.txt"
  ${LineSum} "$TEMP\python.txt" $R0
  IfErrors removePythonChoice
  StrCmp $R0 ${PYTHON_VERSIONS} getPythonInfo
removePythonChoice:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 8" "Type" "Label"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 8" "Text" "No internet connection. Python won't be downloaded."
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 8" "Right" "-1"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 9" "Type" "Label"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 9" "Text" ""
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 9" "Left" "-1"
  goto done
getPythonInfo:
  Call getPythonInfo
  StrCmp $0 "Error" removePythonChoice
; check if pythons are already installed
  StrCpy $R2 $0
  Call searchPython
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 8" "Text" "Python $R2"
  StrCmp $4 "Absent" checkPython3
  StrCpy $R4 "Field 8"
  StrCpy $R5 "Field 9"
  Call updatePythonControls
checkPython3:
  StrCpy $R2 $R0
  Call searchPython
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 9" "Text" "Python $R2"
  StrCmp $4 "Absent" done
  StrCpy $R4 "Field 9"
  StrCpy $R5 "Field 8"
  Call updatePythonControls
done:  
FunctionEnd


Function customSilentConfigReader
  Call customPreInstallActions
  ${LogText} "silent installation, options"

  ${GetParameters} $R0
  ClearErrors

  ${GetOptions} $R0 /CONFIG= $R1
  IfErrors no_silent_config
  ${LogText} "  config file: $R1"

  ${ConfigRead} "$R1" "mode=" $R0
  IfErrors no_silent_config
  ${LogText} "  mode: $R0"
  StrCpy $silentMode "user"
  IfErrors run_in_user_mode
  StrCpy $silentMode $R0

run_in_user_mode:

  ClearErrors
  ${ConfigRead} "$R1" "launcher32=" $R3
  IfErrors launcher_64
  ${LogText} "  shortcut for launcher32: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "State" $R3

launcher_64:
  ClearErrors
  ${ConfigRead} "$R1" "launcher64=" $R3
  IfErrors update_PATH
  ${LogText} "  shortcut for launcher64: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $secondLauncherShortcut" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $secondLauncherShortcut" "State" $R3

update_PATH:
  ClearErrors
  ${ConfigRead} "$R1" "updatePATH=" $R3
  IfErrors download_jre32
  ${LogText} "  update PATH env var: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $addToPath" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $addToPath" "State" $R3

download_jre32:
  ClearErrors
  ${ConfigRead} "$R1" "jre32=" $R3
  IfErrors download_python2
  ${LogText} "  download jre32: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "State" $R3

download_python2:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 8" "State" 0
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 9" "State" 0
  ClearErrors
  ${ConfigRead} "$R1" "python2=" $R3
  IfErrors download_python3
  ${LogText} "  download python2: $R3"
  StrCmp $R3 "1" 0 download_python3
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 8" "State" $R3

download_python3:
  ClearErrors
  ${ConfigRead} "$R1" "python3=" $R3
  IfErrors associations
  ${LogText} "  download python3: $R3"
  StrCmp $R3 "1" 0 associations
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 9" "State" $R3

associations:
  StrCmp "${ASSOCIATION}" "NoAssociation" done
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Settings" "NumFields"
  push "${ASSOCIATION}"
loop:
  call SplitStr
  Pop $0
  StrCmp $0 "" update_settings
  ClearErrors
  ${ConfigRead} "$R1" "$0=" $R3
  IfErrors update_settings
  IntOp $R0 $R0 + 1
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "State" $R3
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Text" "$0"
  ${LogText} "  association: $0, state: $R3"
  Goto loop

update_settings:
  ClearErrors
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  Goto done
no_silent_config:
  Call IncorrectSilentInstallParameters
done:
FunctionEnd


Function customInstallActions
  StrCpy $internetConnection "Yes"
  ${LineSum} "$TEMP\python.txt" $R0
  IfErrors cantOpenFile
; info about 2 and 3 version of python
  StrCmp $R0 ${PYTHON_VERSIONS} getPythonInfo
cantOpenFile:
  ClearErrors
  StrCpy $internetConnection "No"
  Goto check_python
getPythonInfo:  
  Call getPythonInfo
  StrCmp $0 "Error" skip_python_download
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 8" "State"
  StrCpy $R7 "msiexec.exe /i "
  StrCpy $R8 "$0.msi"
  StrCpy $R9 "/quiet /qn"
  StrCmp $R2 1 "" python3
  StrCpy $R2 $0
  StrCpy $R3 $1
  Goto check_python
python3:  
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 9" "State"
  StrCpy $R7 ""
  StrCpy $R8 "$R0.exe"
; install python 3.7 with add to path option only if installation was run from admin
  StrCmp $baseRegKey "HKLM" admin user
admin:
  StrCpy $R9 "InstallAllUsers=1 PrependPath=1 /quiet"
  Goto install
user:
  StrCpy $R9 "InstallAllUsers=0 /quiet"
install:
  StrCmp $R2 1 "" skip_python_download
  StrCpy $R2 $R0
  StrCpy $R3 $R1

check_python:
  Call searchPython
  StrCmp $4 "Absent" get_python python_exists
get_python:
  StrCmp $internetConnection "No" skip_python_download
  CreateDirectory "$INSTDIR\python"
  ${LogText} "download python_$R8"
  inetc::get "$R3" "$INSTDIR\python\python_$R8" /END
  Pop $0
  ${If} $0 == "OK"
    ${LogText} "install python_$R8"
    ExecDos::exec /NOUNLOAD /ASYNC '$R7"$INSTDIR\python\python_$R8" $R9'
  ${Else}
    ${LogText} "ERROR: the python_$R8 download is failed: $0"
    MessageBox MB_OK|MB_ICONEXCLAMATION "The download is failed: $0" /SD IDOK
  ${EndIf}
python_exists:
  Goto done
skip_python_download:
  ${LogText} "ERROR: no internet connection"
done:
FunctionEnd


Function customPostInstallActions
  DetailPrint "There are no custom post-install actions."
FunctionEnd


Function un.customUninstallActions
  DetailPrint "customUninstallActions"
  StrCpy $0 "$INSTDIR\..\python"
  IfFileExists "$0\*.*" 0 no_python
    DetailPrint "install python dir: $0"
    Call un.deleteFiles
    Call un.deleteDirIfEmpty
no_python:
FunctionEnd


Function updatePythonControls
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Text" "Python $R2 (installed)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Flags" "DISABLED"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "State" "0"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R5 "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R5 "State" "0"
FunctionEnd


Function getPythonInfo
  ${LogText} "get Python info"
  ClearErrors
  FileOpen $3 $Temp\python.txt r
; file can not be open.
  IfErrors cantOpenFile
  ${If} ${RunningX64}
    Goto getPythonInfo
  ${Else}
    FileRead $3 $4
    FileRead $3 $4
  ${EndIf}
; get python2 info
getPythonInfo:
  FileRead $3 $4
  ${StrTok} $0 $4 " " "1" "1"
  ${StrTok} $1 $4 " " "2" "1"
; get python3 info
  FileRead $3 $4
  ${StrTok} $R0 $4 " " "1" "1"
  ${StrTok} $R1 $4 " " "2" "1"
  Goto done
cantOpenFile:
  StrCpy $0 "Error"
done:
FunctionEnd


Function searchPython
; $R2 - version of python
  ${LogText} "search a Python version"
  StrCpy $0 "HKCU"
  StrCpy $1 "Software\Python\PythonCore\$R2\InstallPath"
  StrCpy $2 ""
  SetRegView 64
  Call OMReadRegStr
  SetRegView 32
  StrCmp $3 "" CU_32bit verifyPythonLauncher

CU_32bit:
  Call OMReadRegStr
  StrCmp $3 "" installationForAllUsers verifyPythonLauncher

installationForAllUsers:
  StrCpy $0 "HKLM"
  Call OMReadRegStr
  SetRegView 64
  Call OMReadRegStr
  SetRegView 32
  StrCmp $3 "" LM_32bit verifyPythonLauncher

LM_32bit:
  Call OMReadRegStr
  StrCmp $3 "" pythonAbsent
verifyPythonLauncher:
  IfFileExists $3python.exe 0 pythonAbsent
  StrCpy $4 "Exists"
  Goto done
pythonAbsent:
  StrCpy $4 "Absent"
done:
FunctionEnd
