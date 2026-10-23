package uy.nico.barehome;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.nio.file.Files;

public final class DogModeStoreTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Context context = new Context(new File(args[0]));
        DogModeStore store = new DogModeStore(context);
        check("".equals(store.read()), "Missing journal did not remain empty");
        String old = "D1|csv4k-android11-v1|active|ODY0MDAwMDA=|~|MDE=";
        check(store.save(old), "Could not save legacy recovery record");
        check(old.equals(new DogModeStore(context).read()), "A read migrated or expanded legacy originals");
        String five = "D2|csv4k-android11-v2|preparing|00000000000000000000000000000000|ODY0MDAwMDA=|~|MDE=|MQ==|MQ==";
        AtomicFile.failFinish = true;
        check(!store.save(five), "Failed atomic replacement claimed success");
        check(old.equals(new DogModeStore(context).read()), "Failed replacement discarded legacy originals");
        AtomicFile.failFinish = false;
        check(store.save(five) && five.equals(new DogModeStore(context).read()), "Five originals did not survive a fresh disk read");
        check(!store.save(null) && five.equals(store.read()), "Invalid save discarded five-value recovery");
        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i <= DogModeTransaction.MAX_JOURNAL_BYTES; i++) oversized.append('x');
        check(!store.save(oversized.toString()) && five.equals(store.read()), "Oversized save changed recovery state");
        Files.write(new File(args[0], "dog-mode-state").toPath(), new byte[] {(byte) 255});
        boolean rejected = false;
        try { new DogModeStore(context).read(); } catch (IllegalStateException invalid) { rejected = true; }
        check(rejected, "Malformed UTF-8 looked like an empty journal");
        System.out.println("DogModeStore: " + assertions + " behavioral assertions passed");
    }
}
