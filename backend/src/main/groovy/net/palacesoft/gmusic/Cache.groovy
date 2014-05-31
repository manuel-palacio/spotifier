package net.palacesoft.gmusic


class Cache extends LinkedHashMap {

    private final int capacity;

    public Cache(int capacity) {
        super(capacity + 1, 1.1f, true);
        this.capacity = capacity;
    }

    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > capacity;
    }
}
