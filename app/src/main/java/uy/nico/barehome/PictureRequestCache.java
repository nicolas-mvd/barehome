package uy.nico.barehome;

/** Bounded persistent at-most-once cache for recent toggle requests. Stores hashes, not secrets. */
final class PictureRequestCache {
    static final int LIMIT = 16;
    static final int FAILED = -1;
    static final int DUPLICATE = 0;
    static final int NEW = 1;

    interface Store {
        String read();
        boolean save(String ids);
    }

    static int consume(Store store, String id) {
        if (!validId(id)) return FAILED;
        String saved = store.read();
        if (saved == null || saved.length() > LIMIT * 65 - 1) return FAILED;
        String[] ids = saved.isEmpty() ? new String[0] : saved.split(",", -1);
        if (ids.length > LIMIT) return FAILED;
        boolean duplicate = false;
        for (String previous : ids) {
            if (!validId(previous)) return FAILED;
            if (id.equals(previous)) duplicate = true;
        }
        if (duplicate) return DUPLICATE;
        StringBuilder next = new StringBuilder(id);
        for (int i = 0; i < Math.min(ids.length, LIMIT - 1); i++) next.append(',').append(ids[i]);
        return store.save(next.toString()) ? NEW : FAILED;
    }

    private static boolean validId(String id) {
        if (id == null || id.length() != 64) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private PictureRequestCache() {}
}
