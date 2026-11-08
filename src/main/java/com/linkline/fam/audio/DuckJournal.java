package com.linkline.fam.audio;

import java.util.List;

/**
 * Persistent record of sessions whose volume FAM has lowered. If FAM stops in the middle of an
 * announcement, the next start uses this to put the volumes back.
 */
public interface DuckJournal {

    record Entry(String key, String deviceId, String instanceId, float original, float ducked) {
    }

    void save(Entry entry);

    List<Entry> load();

    void clear();
}
