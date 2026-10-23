package uy.nico.barehome;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Private, small journal. Every read comes from disk, including after a failed save. */
final class DogModeStore implements DogModeTransaction.Store {
    private static final Object LOCK = new Object();
    private final File base;
    private final AtomicFile file;

    DogModeStore(Context context) {
        base = new File(context.getFilesDir(), "dog-mode-state");
        file = new AtomicFile(base);
    }

    @Override public String read() {
        synchronized (LOCK) { return readLocked(); }
    }

    private String readLocked() {
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > DogModeTransaction.MAX_JOURNAL_BYTES) {
                    throw new IOException("Dog Mode journal exceeds its bound");
                }
                bytes.write(buffer, 0, count);
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (java.io.FileNotFoundException missing) {
            if (!base.exists() && !new File(base.getPath() + ".bak").exists()) return "";
            throw new IllegalStateException("Could not read Dog Mode journal", missing);
        } catch (IOException failed) {
            throw new IllegalStateException("Could not read Dog Mode journal", failed);
        }
    }

    @Override public boolean save(String record) {
        synchronized (LOCK) { return saveLocked(record); }
    }

    private boolean saveLocked(String record) {
        if (record == null) return false;
        byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > DogModeTransaction.MAX_JOURNAL_BYTES) return false;
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            output.getFD().sync();
            file.finishWrite(output);
            output = null;
            // AtomicFile may report rename failures by logging. Never trust an in-memory value.
            return record.equals(readLocked());
        } catch (IOException | RuntimeException failed) {
            if (output != null) file.failWrite(output);
            return false;
        }
    }
}
