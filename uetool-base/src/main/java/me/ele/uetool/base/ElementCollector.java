package me.ele.uetool.base;

import android.view.View;

import java.util.List;

public interface ElementCollector {

    /**
     * Adds non-View or expanded child elements for the supplied view.
     *
     * @return true when this collector has represented this view's internal tree
     * and normal child traversal should stop.
     */
    boolean collect(View view, List<Element> elements);
}
