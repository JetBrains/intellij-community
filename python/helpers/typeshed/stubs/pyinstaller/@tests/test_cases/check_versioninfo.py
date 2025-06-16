from PyInstaller.utils.win32.versioninfo import (
    FixedFileInfo,
    StringFileInfo,
    StringStruct,
    StringTable,
    VarFileInfo,
    VarStruct,
    VSVersionInfo,
)

# Everything below this line is the content from running `pyi-grab_version python3`
# ==============================================================================

# UTF-8
#
# For more details about fixed file info 'ffi' see:
# http://msdn.microsoft.com/en-us/library/ms646997.aspx
VSVersionInfo(
    ffi=FixedFileInfo(
        # filevers and prodvers should be always a tuple with four items: (1, 2, 3, 4)
        # Set not needed items to zero 0.
        filevers=(3, 13, 1150, 1013),
        prodvers=(3, 13, 1150, 1013),
        # Contains a bitmask that specifies the valid bits 'flags'r
        mask=0x3F,
        # Contains a bitmask that specifies the Boolean attributes of the file.
        flags=0x0,
        # The operating system for which this file was designed.
        # 0x4 - NT and there is no need to change it.
        OS=0x4,
        # The general type of file.
        # 0x1 - the file is an application.
        fileType=0x2,
        # The function of the file.
        # 0x0 - the function is not defined for this fileType
        subtype=0x0,
        # Creation date and time stamp.
        date=(0, 0),
    ),
    kids=[
        StringFileInfo(
            [
                StringTable(
                    "000004b0",
                    [
                        StringStruct("CompanyName", "Python Software Foundation"),
                        StringStruct("FileDescription", "Python Core"),
                        StringStruct("FileVersion", "3.13.1"),
                        StringStruct("InternalName", "Python DLL"),
                        StringStruct(
                            "LegalCopyright",
                            "Copyright © 2001-2024 Python Software Foundation. Copyright © 2000 BeOpen.com. Copyright © 1995-2001 CNRI. Copyright © 1991-1995 SMC.",
                        ),
                        StringStruct("OriginalFilename", "python3.dll"),
                        StringStruct("ProductName", "Python"),
                        StringStruct("ProductVersion", "3.13.1"),
                    ],
                )
            ]
        ),
        VarFileInfo([VarStruct("Translation", [0, 1200])]),
    ],
)
