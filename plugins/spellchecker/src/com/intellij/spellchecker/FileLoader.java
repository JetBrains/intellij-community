/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.spellchecker;

import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.dictionary.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.*;


public class FileLoader implements Loader {

    private String url;

    public FileLoader(String url) {
        this.url = url;
    }

    public void load(@NotNull Processor processor) {

            InputStream io = SpellCheckerManager.class.getResourceAsStream(url);
            DataInputStream in = new DataInputStream(io);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
          try{
            String strLine;
            while ((strLine = br.readLine()) != null) {
                processor.process(strLine);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
              br.close();
            }
            catch (IOException ignored) {

            }
          }
    }

}
