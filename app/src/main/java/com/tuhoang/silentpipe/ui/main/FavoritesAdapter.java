package com.tuhoang.silentpipe.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.data.FavoriteItem;

import java.util.List;

public class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {

    private final List<FavoriteItem> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FavoriteItem item);
        void onDeleteClick(FavoriteItem item);
    }

    public FavoritesAdapter(List<FavoriteItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteItem item = items.get(position);
        holder.textTitle.setText(item.title);
        holder.textUploader.setText(item.uploader);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle;
        TextView textUploader;
        android.widget.ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.tv_title);
            textUploader = itemView.findViewById(R.id.tv_uploader);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
