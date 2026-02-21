package com.tuhoang.silentpipe.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.data.AppDatabase;
import com.tuhoang.silentpipe.data.FavoriteItem;

import java.util.List;

public class FavoritesFragment extends Fragment {

    private RecyclerView recyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_favorites);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        loadFavorites(view);
    }

    private void loadFavorites(View view) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getDatabase(requireContext());
            
            List<FavoriteItem> items = db.favoriteDao().getAll();
            
            requireActivity().runOnUiThread(() -> {

                if (items.isEmpty()) {
                    view.findViewById(R.id.tv_empty).setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    view.findViewById(R.id.tv_empty).setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    FavoritesAdapter adapter = new FavoritesAdapter(items, new FavoritesAdapter.OnItemClickListener() {
                        @Override
                        public void onItemClick(FavoriteItem item) {
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).loadVideo(item.url);
                            }
                        }

                        @Override
                        public void onEditClick(FavoriteItem item) {
                            final android.widget.EditText input = new android.widget.EditText(requireContext());
                            input.setText(item.title);
                            // Add padding to input
                            int pad = (int) (16 * getResources().getDisplayMetrics().density);
                            input.setPadding(pad, pad, pad, pad);
                            
                            new android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Edit Title")
                                .setView(input)
                                .setPositiveButton("Save", (dialog, which) -> {
                                    String newTitle = input.getText().toString().trim();
                                    if (!newTitle.isEmpty()) {
                                        item.title = newTitle;
                                        new Thread(() -> {
                                            AppDatabase db = AppDatabase.getDatabase(requireContext());
                                            db.favoriteDao().update(item);
                                            requireActivity().runOnUiThread(() -> loadFavorites(view));
                                        }).start();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        }

                        @Override
                        public void onDeleteClick(FavoriteItem item) {
                            new android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Delete Favorite")
                                .setMessage("Are you sure you want to delete this item?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    new Thread(() -> {
                                        AppDatabase db = AppDatabase.getDatabase(requireContext());
                                        db.favoriteDao().delete(item);
                                        requireActivity().runOnUiThread(() -> loadFavorites(view)); // Reload list
                                    }).start();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        }
                    });
                    recyclerView.setAdapter(adapter);
                }
            });
        }).start();
    }
}
