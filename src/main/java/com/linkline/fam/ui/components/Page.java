package com.linkline.fam.ui.components;

import com.linkline.fam.AppContext;
import com.linkline.fam.service.EventBus;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * One screen of the application. A page only refreshes while it is visible; when it is shown again
 * it reloads, so pages that are hidden never do work.
 */
public abstract class Page extends JPanel {

    protected final AppContext ctx;
    private final List<Runnable> subscriptions = new ArrayList<>();
    private boolean showing;

    protected Page(AppContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        setOpaque(false);
    }

    public abstract String title();

    public String subtitle() {
        return "";
    }

    /** Reload the data on screen. Always called on the event thread. */
    protected abstract void refresh();

    /** Called when the page becomes visible or hidden, for pages that run timers. */
    protected void visibilityChanged(boolean visible) {
    }

    /** Refresh this page whenever one of the topics is published (while the page is visible). */
    protected final void refreshOn(EventBus.Topic... topics) {
        for (EventBus.Topic t : topics) {
            subscriptions.add(ctx.bus.subscribe(t, () -> {
                if (showing) {
                    refresh();
                }
            }));
        }
    }

    public final void pageShown() {
        showing = true;
        visibilityChanged(true);
        refresh();
    }

    public final void pageHidden() {
        showing = false;
        visibilityChanged(false);
    }

    protected final boolean isShowingPage() {
        return showing;
    }

    public void dispose() {
        subscriptions.forEach(Runnable::run);
        subscriptions.clear();
    }
}
