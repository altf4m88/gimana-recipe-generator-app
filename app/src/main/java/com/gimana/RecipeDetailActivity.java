package com.gimana;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;

public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";
    private static final String TAG = "RecipeDetailActivity";

    private ImageView imageViewRecipeDetail;
    private TextView textViewDetailDescription;
    private TextView textViewDetailIngredients;
    private TextView textViewDetailHowToMake;
    private TextView textViewDetailTips;

    private RecipeDbHelper dbHelper;
    private Markwon markwon;
    private ExecutorService executorService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Recipe Details");
        }

        imageViewRecipeDetail = findViewById(R.id.imageViewRecipeDetail);
        textViewDetailDescription = findViewById(R.id.textViewDetailDescription);
        textViewDetailIngredients = findViewById(R.id.textViewDetailIngredients);
        textViewDetailHowToMake = findViewById(R.id.textViewDetailHowToMake);
        textViewDetailTips = findViewById(R.id.textViewDetailTips);

        dbHelper = new RecipeDbHelper(this);
        markwon = Markwon.create(this);
        executorService = Executors.newSingleThreadExecutor();

        long recipeId = getIntent().getLongExtra(EXTRA_RECIPE_ID, -1);

        if (recipeId != -1) {
            loadRecipeDetails(recipeId);
        } else {
            Toast.makeText(this, getString(R.string.recipe_not_found), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadRecipeDetails(long recipeId) {
        executorService.execute(() -> {
            final Recipe recipe = dbHelper.getRecipeById(recipeId);
            final String imageUriString = dbHelper.getImageUriForRecipe(recipeId);

            runOnUiThread(() -> {
                if (recipe != null) {
                    textViewDetailDescription.setText(recipe.getDescription());
                    markwon.setMarkdown(textViewDetailIngredients, recipe.getIngredients() != null ? recipe.getIngredients() : "");
                    markwon.setMarkdown(textViewDetailHowToMake, recipe.getHowToMake() != null ? recipe.getHowToMake() : "");
                    textViewDetailTips.setText(recipe.getTips());

                    if (imageUriString != null && !imageUriString.isEmpty()) {
                        imageViewRecipeDetail.setVisibility(View.VISIBLE);
                        Glide.with(this)
                                .load(Uri.parse(imageUriString))
                                .placeholder(R.drawable.ic_launcher_background)
                                .error(R.drawable.ic_image_broken)
                                .into(imageViewRecipeDetail);
                    } else {
                        imageViewRecipeDetail.setVisibility(View.GONE);
                        Log.d(TAG, "No image URI found for recipe ID: " + recipeId);
                    }

                } else {
                    Toast.makeText(this, getString(R.string.recipe_not_found), Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}