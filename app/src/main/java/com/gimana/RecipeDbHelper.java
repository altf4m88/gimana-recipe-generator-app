package com.gimana;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "GimanaRecipe.db";
    private static final String TAG = "RecipeDbHelper";


    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + RecipeContract.RecipeEntry.TABLE_NAME + " (" +
                    RecipeContract.RecipeEntry._ID + " INTEGER PRIMARY KEY," +
                    RecipeContract.RecipeEntry.COLUMN_NAME_DESCRIPTION + " TEXT," +
                    RecipeContract.RecipeEntry.COLUMN_NAME_INGREDIENTS + " TEXT," +
                    RecipeContract.RecipeEntry.COLUMN_NAME_HOW_TO_MAKE + " TEXT," +
                    RecipeContract.RecipeEntry.COLUMN_NAME_TIPS + " TEXT," +
                    RecipeContract.RecipeEntry.COLUMN_NAME_IMAGE_URI + " TEXT," +
                    RecipeContract.RecipeEntry.COLUMN_NAME_TIMESTAMP + " INTEGER DEFAULT CURRENT_TIMESTAMP)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + RecipeContract.RecipeEntry.TABLE_NAME;

    public RecipeDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public long addRecipe(Recipe recipe, Uri imageUri) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(RecipeContract.RecipeEntry.COLUMN_NAME_DESCRIPTION, recipe.getDescription());
        values.put(RecipeContract.RecipeEntry.COLUMN_NAME_INGREDIENTS, recipe.getIngredients());
        values.put(RecipeContract.RecipeEntry.COLUMN_NAME_HOW_TO_MAKE, recipe.getHowToMake());
        values.put(RecipeContract.RecipeEntry.COLUMN_NAME_TIPS, recipe.getTips());
        if (imageUri != null) {
            values.put(RecipeContract.RecipeEntry.COLUMN_NAME_IMAGE_URI, imageUri.toString());
        } else {
            values.putNull(RecipeContract.RecipeEntry.COLUMN_NAME_IMAGE_URI);
        }
        values.put(RecipeContract.RecipeEntry.COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());

        long newRowId = db.insert(RecipeContract.RecipeEntry.TABLE_NAME, null, values);
        return newRowId;
    }

    public Recipe getRecipeById(long recipeId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Recipe recipe = null;

        String[] projection = {
                RecipeContract.RecipeEntry._ID,
                RecipeContract.RecipeEntry.COLUMN_NAME_DESCRIPTION,
                RecipeContract.RecipeEntry.COLUMN_NAME_INGREDIENTS,
                RecipeContract.RecipeEntry.COLUMN_NAME_HOW_TO_MAKE,
                RecipeContract.RecipeEntry.COLUMN_NAME_TIPS
        };

        String selection = RecipeContract.RecipeEntry._ID + " = ?";
        String[] selectionArgs = { String.valueOf(recipeId) };

        Cursor cursor = db.query(
                RecipeContract.RecipeEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null, null, null
        );

        if (cursor.moveToFirst()) {
            recipe = new Recipe();
            recipe.setId(cursor.getLong(cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry._ID)));
            recipe.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_DESCRIPTION)));
            recipe.setIngredients(cursor.getString(cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_INGREDIENTS)));
            recipe.setHowToMake(cursor.getString(cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_HOW_TO_MAKE)));
            recipe.setTips(cursor.getString(cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_TIPS)));
        }
        cursor.close();
        return recipe;
    }

    public String getImageUriForRecipe(long recipeId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String imageUri = null;
        Cursor cursor = null;
        try {
            cursor = db.query(
                    RecipeContract.RecipeEntry.TABLE_NAME,
                    new String[]{RecipeContract.RecipeEntry.COLUMN_NAME_IMAGE_URI},
                    RecipeContract.RecipeEntry._ID + "=?",
                    new String[]{String.valueOf(recipeId)},
                    null, null, null
            );
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_IMAGE_URI);
                if (!cursor.isNull(columnIndex)) {
                    imageUri = cursor.getString(columnIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching image URI for recipe ID: " + recipeId, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return imageUri;
    }


    public Map<String, List<?>> getAllRecipesWithUriAndTimestamp() {
        List<Recipe> recipeList = new ArrayList<>();
        List<String> imageUriList = new ArrayList<>();
        List<Long> timestampList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            String[] projection = {
                    RecipeContract.RecipeEntry._ID,
                    RecipeContract.RecipeEntry.COLUMN_NAME_DESCRIPTION,
                    RecipeContract.RecipeEntry.COLUMN_NAME_INGREDIENTS,
                    RecipeContract.RecipeEntry.COLUMN_NAME_HOW_TO_MAKE,
                    RecipeContract.RecipeEntry.COLUMN_NAME_TIPS,
                    RecipeContract.RecipeEntry.COLUMN_NAME_IMAGE_URI,
                    RecipeContract.RecipeEntry.COLUMN_NAME_TIMESTAMP
            };
            String sortOrder = RecipeContract.RecipeEntry.COLUMN_NAME_TIMESTAMP + " DESC";

            cursor = db.query(
                    RecipeContract.RecipeEntry.TABLE_NAME,
                    projection, null, null, null, null, sortOrder
            );

            int idColumn = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry._ID);
            int descColumn = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_DESCRIPTION);
            int ingrColumn = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_INGREDIENTS);
            int howToColumn = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_HOW_TO_MAKE);
            int tipsColumn = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_TIPS);
            int imageUriColumn = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_IMAGE_URI);
            int timestampColumn = cursor.getColumnIndexOrThrow(RecipeContract.RecipeEntry.COLUMN_NAME_TIMESTAMP);


            while (cursor.moveToNext()) {
                Recipe recipe = new Recipe();
                recipe.setId(cursor.getLong(idColumn));
                recipe.setDescription(cursor.getString(descColumn));
                recipe.setIngredients(cursor.getString(ingrColumn));
                recipe.setHowToMake(cursor.getString(howToColumn));
                recipe.setTips(cursor.getString(tipsColumn));
                recipeList.add(recipe);

                if (!cursor.isNull(imageUriColumn)) {
                    imageUriList.add(cursor.getString(imageUriColumn));
                } else {
                    imageUriList.add(null);
                }
                if (!cursor.isNull(timestampColumn)) {
                    timestampList.add(cursor.getLong(timestampColumn));
                } else {
                    timestampList.add(0L);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching all recipes with details", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Map<String, List<?>> result = new HashMap<>();
        result.put("recipes", recipeList);
        result.put("imageUris", imageUriList);
        result.put("timestamps", timestampList);
        return result;
    }
}