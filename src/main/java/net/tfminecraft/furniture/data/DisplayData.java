package net.tfminecraft.furniture.data;

import org.bukkit.configuration.ConfigurationSection;

public class DisplayData {
    private float xRot;
    private float yRot;
    private float zRot;

    private float xScale;
    private float yScale;
    private float zScale;

    private float xPos;
    private float yPos;
    private float zPos;

    public DisplayData(ConfigurationSection config) {
        // Default values
        this.xRot = 0f;
        this.yRot = 0f;
        this.zRot = 0f;

        this.xScale = 1f;
        this.yScale = 1f;
        this.zScale = 1f;

        this.xPos = 0f;
        this.yPos = 0f;
        this.zPos = 0f;

        // ------------ ROTATION ------------
        ConfigurationSection rot = config.getConfigurationSection("rotation");
        if (rot != null) {
            this.xRot = (float) rot.getDouble("x", this.xRot);
            this.yRot = (float) rot.getDouble("y", this.yRot);
            this.zRot = (float) rot.getDouble("z", this.zRot);
        }

        // ------------ SCALE ------------
        ConfigurationSection scale = config.getConfigurationSection("scale");
        if (scale != null) {
            this.xScale = (float) scale.getDouble("x", this.xScale);
            this.yScale = (float) scale.getDouble("y", this.yScale);
            this.zScale = (float) scale.getDouble("z", this.zScale);
        }

        // ------------ POSITION ------------
        ConfigurationSection pos = config.getConfigurationSection("position");
        if (pos != null) {
            this.xPos = (float) pos.getDouble("x", this.xPos);
            this.yPos = (float) pos.getDouble("y", this.yPos);
            this.zPos = (float) pos.getDouble("z", this.zPos);
        }
    }


    public DisplayData() {
        this.xRot = 0;
        this.yRot = 0;
        this.zRot = 0;
        this.xScale = 1;
        this.yScale = 1;
        this.zScale = 1;
        this.xPos = 0;
        this.yPos = 0;
        this.zPos = 0;
    }

    public float getxRot() {
        return xRot;
    }
    public float getyRot() {
        return yRot;
    }
    public float getzRot() {
        return zRot;
    }

    public float getxScale() {
        return xScale;
    }
    public float getyScale() {
        return yScale;
    }
    public float getzScale() {
        return zScale;
    }

    public float getxPos() {
        return xPos;
    }
    public float getyPos() {
        return yPos;
    }
    public float getzPos() {
        return zPos;
    }

    // --- Setters ---

    public void setxRot(float xRot) {
        this.xRot = xRot;
    }
    public void setyRot(float yRot) {
        this.yRot = yRot;
    }
    public void setzRot(float zRot) {
        this.zRot = zRot;
    }

    public void setxScale(float xScale) {
        this.xScale = xScale;
    }
    public void setyScale(float yScale) {
        this.yScale = yScale;
    }
    public void setzScale(float zScale) {
        this.zScale = zScale;
    }

    public void setxPos(float xPos) {
        this.xPos = xPos;
    }
    public void setyPos(float yPos) {
        this.yPos = yPos;
    }
    public void setzPos(float zPos) {
        this.zPos = zPos;
    }

}