package com.linkline.fam.ui.components;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.util.Fmt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.table.AbstractTableModel;

/** Table model for audio files, shared by the Audio Library page and the audio picker. */
public final class AudioTableModel extends AbstractTableModel {

    public enum Filter {
        ALL("All files"), ASSIGNED("Assigned"), UNASSIGNED("Not assigned");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Everything needed to show the library, loaded off the event thread. */
    public record Data(List<AudioItem> items, Map<Long, List<String>> usage, Set<Long> missing) {
    }

    private static final String[] COLUMNS = {"Name", "Format", "Duration", "Size", "Imported", "Assigned to"};

    private List<AudioItem> all = List.of();
    private List<AudioItem> view = List.of();
    private Map<Long, List<String>> usage = Map.of();
    private Set<Long> missing = Set.of();
    private String text = "";
    private Filter filter = Filter.ALL;

    /** Reads the library from the database and checks that each file still exists. Call off the event thread. */
    public static Data load(AppContext ctx) {
        List<AudioItem> items = ctx.library.list();
        Map<Long, List<String>> usage = new HashMap<>();
        for (Schedule s : ctx.schedules.list()) {
            if (s.getAudioId() != null) {
                usage.computeIfAbsent(s.getAudioId(), k -> new ArrayList<>()).add(s.getName());
            }
        }
        Set<Long> missing = new HashSet<>();
        for (AudioItem a : items) {
            if (!ctx.library.fileExists(a)) {
                missing.add(a.id());
            }
        }
        return new Data(items, usage, missing);
    }

    public void setData(Data d) {
        all = d.items();
        usage = d.usage();
        missing = d.missing();
        applyFilter();
    }

    public void setFilter(String text, Filter filter) {
        this.text = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        this.filter = filter == null ? Filter.ALL : filter;
        applyFilter();
    }

    private void applyFilter() {
        List<AudioItem> out = new ArrayList<>();
        for (AudioItem a : all) {
            boolean assigned = usage.containsKey(a.id());
            if (filter == Filter.ASSIGNED && !assigned || filter == Filter.UNASSIGNED && assigned) {
                continue;
            }
            if (!text.isEmpty() && !a.displayName().toLowerCase(Locale.ROOT).contains(text)
                    && !a.originalName().toLowerCase(Locale.ROOT).contains(text)) {
                continue;
            }
            out.add(a);
        }
        view = out;
        fireTableDataChanged();
    }

    public AudioItem itemAt(int row) {
        return row >= 0 && row < view.size() ? view.get(row) : null;
    }

    public int rowOf(long audioId) {
        for (int i = 0; i < view.size(); i++) {
            if (view.get(i).id() == audioId) {
                return i;
            }
        }
        return -1;
    }

    public int totalCount() {
        return all.size();
    }

    public List<AudioItem> allItems() {
        return all;
    }

    public List<String> usedBy(long audioId) {
        return usage.getOrDefault(audioId, List.of());
    }

    @Override
    public int getRowCount() {
        return view.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int c) {
        return COLUMNS[c];
    }

    @Override
    public Object getValueAt(int row, int col) {
        AudioItem a = view.get(row);
        switch (col) {
            case 0: {
                boolean gone = missing.contains(a.id());
                return new Tables.IconText(
                        Icons.of(gone ? Glyph.WARN : Glyph.MUSIC, 18, gone ? Theme.WARNING : Theme.TEXT_MUTED),
                        a.displayName(), null,
                        gone ? "The file is missing from FAM's storage. Delete it and import it again."
                                : "Original file: " + a.originalName());
            }
            case 1:
                return a.format();
            case 2:
                return Fmt.duration(a.durationMs());
            case 3:
                return Fmt.size(a.sizeBytes());
            case 4:
                return Fmt.dateTime(a.importedAt());
            default: {
                List<String> names = usage.get(a.id());
                if (names == null || names.isEmpty()) {
                    return new Tables.IconText(Ui.dot(Theme.TEXT_FAINT, 14), "Not assigned", Theme.TEXT_FAINT, null);
                }
                String joined = String.join(", ", names);
                return new Tables.IconText(Ui.dot(Theme.SUCCESS, 14), joined, null, "Used by: " + joined);
            }
        }
    }
}
