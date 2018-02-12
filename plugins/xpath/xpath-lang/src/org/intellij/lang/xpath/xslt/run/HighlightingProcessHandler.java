/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;

import java.io.OutputStream;
import java.io.Reader;

class HighlightingProcessHandler extends ProcessHandler {
    private final HighlightingProcessReader myProcessReader;

    public HighlightingProcessHandler(Reader stream) {
        myProcessReader = new HighlightingProcessReader(stream, this);
    }

    public void start() {
        myProcessReader.start();
    }

    @Override
    protected void destroyProcessImpl() {
    }

    @Override
    protected void detachProcessImpl() {
    }

    @Override
    public boolean detachIsDefault() {
        return false;
    }

    @Override
    @SuppressWarnings({"ConstantConditions"})
    public OutputStream getProcessInput() {
        return null;
    }

    private static class HighlightingProcessReader extends ReadProcessThread {
        private final ProcessHandler myHandler;

        public HighlightingProcessReader(Reader stream, ProcessHandler handler) {
            super(stream);
            myHandler = handler;
        }

        public void run() {
            myHandler.startNotify();
            super.run();
        }

        protected void textAvailable(final String s) {
            myHandler.notifyTextAvailable(s, ProcessOutputTypes.SYSTEM);
        }
    }
}
