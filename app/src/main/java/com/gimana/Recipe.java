package com.gimana;

import com.google.gson.annotations.SerializedName;

public class Recipe {

    @SerializedName("description")
    private String description;

    @SerializedName("ingredients")
    private String ingredients;

    @SerializedName("how_to_make")
    private String howToMake;

    @SerializedName("tips")
    private String tips;

    public Recipe() {
    }

    public Recipe(String description, String ingredients, String howToMake, String tips) {
        this.description = description;
        this.ingredients = ingredients;
        this.howToMake = howToMake;
        this.tips = tips;
    }

    public String getDescription() {
        return description;
    }

    public String getIngredients() {
        return ingredients;
    }

    public String getHowToMake() {
        return howToMake;
    }

    public String getTips() {
        return tips;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIngredients(String ingredients) {
        this.ingredients = ingredients;
    }

    public void setHowToMake(String howToMake) {
        this.howToMake = howToMake;
    }

    public void setTips(String tips) {
        this.tips = tips;
    }

    private long id;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}