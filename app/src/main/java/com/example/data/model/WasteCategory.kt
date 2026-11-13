package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class WasteCategory(
    val label: String,
    val subtitle: String,
    val defaultBin: String,
    val examples: List<String>,
    val sortingGuide: String,
    val ecoFact: String,
    val accentColor: Color,
    val containerColor: Color,
) {
    PLASTIC(
        label = "Plastic",
        subtitle = "Bottles, containers, wrappers",
        defaultBin = "Blue Recycling Bin",
        examples = listOf("PET water bottles", "HDPE milk jugs", "Food containers", "Plastic cups"),
        sortingGuide = "Empty and lightly rinse containers. Keep caps attached if locally accepted, and avoid bagging loose recyclables.",
        ecoFact = "Recycling 1 plastic bottle saves enough energy to power a 60W LED bulb for 6 hours.",
        accentColor = Color(0xFF0277BD),
        containerColor = Color(0xFFE1F5FE),
    ),
    PAPER(
        label = "Paper",
        subtitle = "Newspapers, cardboard, sheets",
        defaultBin = "Paper & Cardboard Bin",
        examples = listOf("Corrugated cardboard", "Office paper", "Newspapers", "Paperboard boxes"),
        sortingGuide = "Flatten cardboard boxes and keep paper clean and dry. Exclude grease-stained food boxes.",
        ecoFact = "Recycling one ton of paper saves roughly 17 trees and 7,000 gallons of water.",
        accentColor = Color(0xFFD97706),
        containerColor = Color(0xFFFEF3C7),
    ),
    METAL(
        label = "Metal",
        subtitle = "Aluminum cans, metal scraps",
        defaultBin = "Yellow Metal Recycling Bin",
        examples = listOf("Aluminum soda cans", "Tin food cans", "Clean foil trays", "Steel lids"),
        sortingGuide = "Drain liquids and rinse food residue from tin or aluminum cans before placing in the metal recycling stream.",
        ecoFact = "Aluminum can be recycled indefinitely and saves up to 95% of the energy needed to make new metal.",
        accentColor = Color(0xFF475569),
        containerColor = Color(0xFFE2E8F0),
    ),
    GLASS(
        label = "Glass",
        subtitle = "Glass bottles and jars",
        defaultBin = "Glass Collection Bank",
        examples = listOf("Clear & tinted bottles", "Condiment jars", "Beverage glass"),
        sortingGuide = "Rinse glass bottles and jars, remove metal lids to recycle separately, and avoid mixing broken ceramics or Pyrex.",
        ecoFact = "Glass is 100% recyclable and can be remelted endlessly without losing purity or quality.",
        accentColor = Color(0xFF0D9488),
        containerColor = Color(0xFFCCFBF1),
    ),
    ORGANIC(
        label = "Organic",
        subtitle = "Food scraps, vegetable peels",
        defaultBin = "Green Compost / Wet Bin",
        examples = listOf("Fruit & vegetable peels", "Coffee grounds", "Garden leaves", "Food scraps"),
        sortingGuide = "Place biodegradable food scraps and yard trimmings into your green wet-waste or compost bin without plastic liners.",
        ecoFact = "Composting organic waste prevents methane emissions in landfills and creates nutrient-rich soil.",
        accentColor = Color(0xFF15803D),
        containerColor = Color(0xFFDCFCE7),
    ),
    OTHER(
        label = "Other",
        subtitle = "Composite or unrecognized items",
        defaultBin = "General / Special Waste",
        examples = listOf("Multi-layer wrappers", "Ceramics", "E-waste", "Unrecognized items"),
        sortingGuide = "Check local municipal rules. Batteries, electronics, and hazardous items must go to dedicated drop-off centers.",
        ecoFact = "Separating non-recyclables prevents contamination of clean paper and plastic recycling batches.",
        accentColor = Color(0xFF9333EA),
        containerColor = Color(0xFFF3E8FF),
    );

    companion object {
        fun fromLabel(label: String): WasteCategory {
            return entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) } ?: OTHER
        }
    }
}
