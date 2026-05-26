package com.storm.coldwind

sealed class MenuItem {
    data class Switch(
        val id: String,
        val title: String,
        var enabled: Boolean
    ) : MenuItem()

    data class Counter(
        val id: String,
        val title: String,
        var value: Int
    ) : MenuItem()

    data class Selector(
        val id: String,
        val title: String,
        val options: List<String>,
        var selected: String
    ) : MenuItem()
}

data class MenuPage(
    val id: String,
    val title: String,
    val items: MutableList<MenuItem>
)