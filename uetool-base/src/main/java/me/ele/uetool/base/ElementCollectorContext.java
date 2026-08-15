package me.ele.uetool.base;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface ElementCollectorContext {

    /**
     * Returns a real View subtree to the standard UETool traversal pipeline.
     */
    void collectViewTree(@NonNull View view, @Nullable Element parentElement);
}
