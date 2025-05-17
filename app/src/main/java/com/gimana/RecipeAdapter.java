package com.gimana; // Replace with your package name

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
// You need to add a method in RecipeDbHelper to get image URI and timestamp
// And update the Recipe model or pass these separately.
// For simplicity, I'll assume Recipe model might hold them or we fetch them separately.

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    private Context context;
    private List<Recipe> recipeList; // Your Recipe model
    private List<String> recipeImageUris; // Parallel list for image URIs
    private List<Long> recipeTimestamps; // Parallel list for timestamps
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Recipe recipe);
    }

    // Modified constructor
    public RecipeAdapter(Context context, List<Recipe> recipeList, List<String> recipeImageUris, List<Long> recipeTimestamps, OnItemClickListener listener) {
        this.context = context;
        this.recipeList = recipeList;
        this.recipeImageUris = recipeImageUris;
        this.recipeTimestamps = recipeTimestamps;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_recipe, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        Recipe recipe = recipeList.get(position);
        String imageUriString = (recipeImageUris != null && position < recipeImageUris.size()) ? recipeImageUris.get(position) : null;
        Long timestamp = (recipeTimestamps != null && position < recipeTimestamps.size()) ? recipeTimestamps.get(position) : 0L;

        holder.textViewDescription.setText(recipe.getDescription());

        if (timestamp != null && timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            holder.textViewTimestamp.setText(context.getString(R.string.saved_on_prefix) + sdf.format(new Date(timestamp))); // Added prefix
            holder.textViewTimestamp.setVisibility(View.VISIBLE);
        } else {
            holder.textViewTimestamp.setVisibility(View.GONE);
        }

        if (imageUriString != null && !imageUriString.isEmpty()) {
            Glide.with(context)
                    .load(Uri.parse(imageUriString))
                    .placeholder(R.mipmap.ic_launcher) // Or a more specific placeholder
                    .error(R.drawable.ic_image_broken)   // Ensure you have ic_image_broken.xml
                    .into(holder.imageViewThumbnail);
        } else {
            // Set a default image or hide if no URI
            holder.imageViewThumbnail.setImageResource(R.mipmap.ic_launcher); // Default placeholder
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(recipe); // recipe object has the ID
            }
        });
    }

    @Override
    public int getItemCount() {
        return recipeList.size();
    }

    public void updateRecipes(List<Recipe> newRecipeList, List<String> newImageUris, List<Long> newTimestamps) {
        this.recipeList.clear();
        this.recipeList.addAll(newRecipeList);

        this.recipeImageUris.clear();
        this.recipeImageUris.addAll(newImageUris);

        this.recipeTimestamps.clear();
        this.recipeTimestamps.addAll(newTimestamps);

        notifyDataSetChanged();
    }

    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewThumbnail;
        TextView textViewDescription;
        TextView textViewTimestamp;

        public RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewThumbnail = itemView.findViewById(R.id.imageViewItemRecipeThumbnail);
            textViewDescription = itemView.findViewById(R.id.textViewItemRecipeDescription);
            textViewTimestamp = itemView.findViewById(R.id.textViewItemRecipeTimestamp);
        }
    }
}