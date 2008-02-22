package com.jetbrains.python.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.project.Project;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author yole
 */
public class PythonTracebackFilter implements Filter {
    private Project myProject;
    private Pattern _pattern = Pattern.compile("File \"([^\"]+)\"\\, line (\\d+)\\, in");

    public PythonTracebackFilter(Project myProject) {
        this.myProject = myProject;
    }

    public Result applyFilter(String line, int entireLength) {
        //   File "C:\Progs\Crack\psidc\scummdc.py", line 72, in ?
        Matcher matcher = _pattern.matcher(line);
        if (matcher.find()) {
            String fileName = matcher.group(1).replace('\\', '/');
            int lineNumber = Integer.parseInt(matcher.group(2));
            VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fileName);
            if (vFile != null) {
                OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(myProject, vFile, lineNumber-1);
                final int textStartOffset = entireLength - line.length();
                int startPos = line.indexOf('\"') + 1;
                int endPos = line.indexOf('\"', startPos);
                return new Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink);
            }
        }
        return null;
    }
}
