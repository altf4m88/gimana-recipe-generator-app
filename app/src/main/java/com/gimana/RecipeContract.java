package com.gimana; // Replace with your package name

import android.provider.BaseColumns;

public final class RecipeContract {
    private RecipeContract() {}

    public static class RecipeEntry implements BaseColumns {
        public static final String TABLE_NAME = "recipes";
        public static final String COLUMN_NAME_DESCRIPTION = "description";
        public static final String COLUMN_NAME_INGREDIENTS = "ingredients";
        public static final String COLUMN_NAME_HOW_TO_MAKE = "how_to_make";
        public static final String COLUMN_NAME_TIPS = "tips";
        public static final String COLUMN_NAME_IMAGE_URI = "image_uri";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    }
}