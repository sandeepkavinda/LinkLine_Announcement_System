package com.linkline.fam.ui.components;

import com.linkline.fam.model.PlayStatus;
import com.linkline.fam.service.SchedulerService.TimelineStatus;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Icons.Glyph;
import java.awt.Color;
import javax.swing.Icon;

/** How each announcement status looks: a glyph and a color, shown as an icon rather than a text badge. */
public final class StatusStyle {

    private StatusStyle() {
    }

    public static Color color(PlayStatus s) {
        switch (s) {
            case COMPLETED:
                return Theme.SUCCESS;
            case PLAYING:
                return Theme.ACCENT;
            case QUEUED:
                return Theme.WARNING;
            case FAILED:
                return Theme.DANGER;
            case SKIPPED:
            default:
                return Theme.TEXT_FAINT;
        }
    }

    public static Glyph glyph(PlayStatus s) {
        switch (s) {
            case COMPLETED:
                return Glyph.CHECK;
            case PLAYING:
                return Glyph.WAVE;
            case QUEUED:
                return Glyph.CLOCK;
            case FAILED:
                return Glyph.CLOSE;
            case SKIPPED:
            default:
                return Glyph.MINUS;
        }
    }

    public static Icon marker(PlayStatus s, int size) {
        return Ui.marker(glyph(s), color(s), size);
    }

    public static Color color(TimelineStatus s) {
        switch (s) {
            case COMPLETED:
                return Theme.SUCCESS;
            case PLAYING:
                return Theme.ACCENT;
            case QUEUED:
                return Theme.WARNING;
            case FAILED:
                return Theme.DANGER;
            case SKIPPED:
                return Theme.TEXT_FAINT;
            case UPCOMING:
            default:
                return Theme.TEXT_MUTED;
        }
    }

    public static Glyph glyph(TimelineStatus s) {
        switch (s) {
            case COMPLETED:
                return Glyph.CHECK;
            case PLAYING:
                return Glyph.WAVE;
            case QUEUED:
            case UPCOMING:
                return Glyph.CLOCK;
            case FAILED:
                return Glyph.CLOSE;
            case SKIPPED:
            default:
                return Glyph.MINUS;
        }
    }

    public static Icon marker(TimelineStatus s, int size) {
        return Ui.marker(glyph(s), color(s), size);
    }
}
