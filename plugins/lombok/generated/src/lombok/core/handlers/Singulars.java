/*
 * Copyright (C) 2015 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.core.handlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Singulars {
	private static final List<String> SINGULAR_STORE; // intended to be immutable.
	
	static {
		SINGULAR_STORE = new ArrayList<String>();
		
		try {
			InputStream in = Singulars.class.getResourceAsStream("singulars.txt");
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
				for (String line = br.readLine(); line != null; line = br.readLine()) {
					line = line.trim();
					if (line.startsWith("#") || line.isEmpty()) continue;
					if (line.endsWith(" =")) {
						SINGULAR_STORE.add(line.substring(0, line.length() - 2));
						SINGULAR_STORE.add("");
						continue;
					}
					
					int idx = line.indexOf(" = ");
					SINGULAR_STORE.add(line.substring(0, idx));
					SINGULAR_STORE.add(line.substring(idx + 3));
				}
			} finally {
				try {
					in.close();
				} catch (Throwable ignore) {}
			}
		} catch (IOException e) {
			SINGULAR_STORE.clear();
		}
	}
	
	public static String autoSingularize(String in) {
		final int inLen = in.length();
		for (int i = 0; i < SINGULAR_STORE.size(); i+= 2) {
			final String lastPart = SINGULAR_STORE.get(i);
			final boolean wholeWord = Character.isUpperCase(lastPart.charAt(0));
			final int endingOnly = lastPart.charAt(0) == '-' ? 1 : 0;
			final int len = lastPart.length();
			if (inLen < len) continue;
			if (!in.regionMatches(true, inLen - len + endingOnly, lastPart, endingOnly, len - endingOnly)) continue;
			if (wholeWord && inLen != len && !Character.isUpperCase(in.charAt(inLen - len))) continue;
			
			String replacement = SINGULAR_STORE.get(i + 1);
			if (replacement.equals("!")) return null;
			
			boolean capitalizeFirst = !replacement.isEmpty() && Character.isUpperCase(in.charAt(inLen - len + endingOnly));
			String pre = in.substring(0, inLen - len + endingOnly);
			String post = capitalizeFirst ? Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1) : replacement;
			return pre + post;
		}
		
		return null;
	}
}
