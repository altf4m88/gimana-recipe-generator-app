package com.gimana;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerViewHistory;
    private RecipeAdapter recipeAdapter;
    private RecipeDbHelper dbHelper;
    private TextView textViewEmptyHistory;
    private ExecutorService executorService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Recipe History");
        }

        recyclerViewHistory = findViewById(R.id.recyclerViewHistory);
        textViewEmptyHistory = findViewById(R.id.textViewEmptyHistory);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));

        dbHelper = new RecipeDbHelper(this);
        executorService = Executors.newSingleThreadExecutor();

        recipeAdapter = new RecipeAdapter(this, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), recipe -> {
            Intent intent = new Intent(HistoryActivity.this, RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.getId());
            startActivity(intent);
        });
        recyclerViewHistory.setAdapter(recipeAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecipeHistory();
    }

    private void loadRecipeHistory() {
        executorService.execute(() -> {
            Map<String, List<?>> historyData = dbHelper.getAllRecipesWithUriAndTimestamp();

            List<Recipe> recipes = (List<Recipe>) historyData.get("recipes");
            List<String> imageUris = (List<String>) historyData.get("imageUris");
            List<Long> timestamps = (List<Long>) historyData.get("timestamps");

            runOnUiThread(() -> {
                if (recipes != null && !recipes.isEmpty()) {
                    recipeAdapter.updateRecipes(recipes, imageUris, timestamps);
                    recyclerViewHistory.setVisibility(View.VISIBLE);
                    textViewEmptyHistory.setVisibility(View.GONE);
                } else {
                    recyclerViewHistory.setVisibility(View.GONE);
                    textViewEmptyHistory.setVisibility(View.VISIBLE);
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