/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.summon.api;

import java.io.File;
import java.io.IOException;

public final class IO {
    private IO() {
    }

    public static File userHome() {
        return new File(System.getProperty("user.home"));
    }

    public static File which(String cmd) {
        String path;
        File file;

        path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(":")) {
                file = new File(entry.trim(), cmd);
                if (file.isFile()) {
                    return file;
                }
            }
        }
        return null;
    }

    public static File resolveSymbolicLinks(File originalFile) throws IOException {
        return originalFile.toPath().toRealPath().toFile();
    }
}
