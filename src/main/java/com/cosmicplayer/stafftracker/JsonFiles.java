package com.cosmicplayer.stafftracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One shared writer for the mod's JSON files. Writing goes through a temp
 * file that moves into place, so a crash mid write cannot corrupt data.
 */
final class JsonFiles {
    private JsonFiles() {
    }

    static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, json);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
