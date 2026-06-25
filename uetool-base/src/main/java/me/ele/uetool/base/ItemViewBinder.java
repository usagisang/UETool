package me.ele.uetool.base;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

/**
 * @author: weishenhong <a href="mailto:weishenhong@bytedance.com">contact me.</a>
 * @date: 2019-07-08 22:51
 */
public interface ItemViewBinder<T, VH extends RecyclerView.ViewHolder> {

    @NonNull
    VH onCreateViewHolder(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent, RecyclerView.Adapter adapter);

    void onBindViewHolder(@NonNull VH holder, @NonNull T item);

}


