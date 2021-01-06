/*-
 * #%L
 * Checkt
 * %%
 * Copyright (C) 2020 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.checkt.apt;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

final class SourceWriter implements Closeable {
    private final Writer out;

    SourceWriter(Writer out) {
        this.out = out;
    }

    void print(Object o) throws IOException {
        out.write(o.toString());
    }

    void println(Object o) throws IOException {
        print(o);
        println();
    }

    void println() throws IOException {
        out.write(System.getProperty("line.separator"));
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
