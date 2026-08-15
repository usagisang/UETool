package me.ele.uetool.base;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.List;

public interface ContextAwareElementCollector extends ElementCollector {

    /**
     * Collects expanded elements and may delegate real View subtrees back to the standard traversal.
     */
    boolean collect(@NonNull View view, @NonNull List<Element> elements,
                    @NonNull ElementCollectorContext context);
}
